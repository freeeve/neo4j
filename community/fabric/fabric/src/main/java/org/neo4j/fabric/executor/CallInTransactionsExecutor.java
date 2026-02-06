/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.fabric.executor;

import static scala.jdk.javaapi.CollectionConverters.asJava;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.neo4j.bolt.protocol.common.message.AccessMode;
import org.neo4j.cypher.internal.ast.SubqueryCall;
import org.neo4j.cypher.internal.expressions.ExplicitParameter;
import org.neo4j.cypher.internal.expressions.SignedDecimalIntegerLiteral;
import org.neo4j.cypher.internal.logical.plans.TransactionForeach$;
import org.neo4j.cypher.internal.preparser.FullyParsedQuery;
import org.neo4j.exceptions.ParameterNotFoundException;
import org.neo4j.fabric.eval.Catalog;
import org.neo4j.fabric.eval.UseEvaluation;
import org.neo4j.fabric.planning.FabricPlan;
import org.neo4j.fabric.planning.FabricPlanner;
import org.neo4j.fabric.planning.Fragment;
import org.neo4j.fabric.stream.DelegatingFragmentResult;
import org.neo4j.fabric.stream.FragmentResult;
import org.neo4j.fabric.stream.QueryInput;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.Records;
import org.neo4j.fabric.stream.StatementResults;
import org.neo4j.fabric.stream.summary.PlanlessSummary;
import org.neo4j.fabric.transaction.FabricTransaction;
import org.neo4j.fabric.transaction.TransactionMode;
import org.neo4j.graphdb.QueryExecutionType;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.impl.query.QueryRoutingMonitor;
import org.neo4j.notifications.NotificationImplementation;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.LongValue;
import org.neo4j.values.storable.NoValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.ListValueBuilder;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.MapValueBuilder;

class CallInTransactionsExecutor extends SingleQueryFragmentExecutor {

    private final Fragment.Apply callInTransactions;
    private final Fragment.Exec innerFragment;
    private final QueryExecutionType resultExecutionType;
    private final int batchSize;
    private final List<BufferedInputRow> inputRowsBuffer;
    private final ProfilingContext profilingContext;
    private Catalog.Graph batchGraph;
    private TransactionMode batchTransactionMode;
    private OnErrorBreakContext onErrorBreakContext;

    CallInTransactionsExecutor(
            Fragment.Apply callInTransactions,
            FabricPlanner.PlannerInstance plannerInstance,
            FabricTransaction.FabricExecutionContext ctx,
            UseEvaluation.Instance useEvaluator,
            FabricPlan plan,
            MapValue queryParams,
            AccessMode accessMode,
            QueryStatementLifecycles.StatementLifecycle lifecycle,
            QueryRoutingMonitor queryRoutingMonitor,
            Tracer tracer,
            QueryExecutionType resultExecutionType,
            ProfilingContext profilingContext,
            FragmentExecutor fragmentExecutor) {
        super(
                plannerInstance,
                ctx,
                useEvaluator,
                plan,
                queryParams,
                accessMode,
                lifecycle,
                queryRoutingMonitor,
                tracer,
                fragmentExecutor);
        this.callInTransactions = callInTransactions;
        this.innerFragment = (Fragment.Exec) callInTransactions.inner();
        this.batchSize = batchSize();
        this.resultExecutionType = resultExecutionType;
        this.profilingContext = profilingContext;
        inputRowsBuffer = new ArrayList<>(batchSize);
        this.onErrorBreakContext = onErrorBreakContext();
    }

    private OnErrorBreakContext onErrorBreakContext() {
        var parameters = callInTransactions.inTransactionsParameters().get();
        if (!CallInTransactionsExecutorUtil.isOnErrorBreak(parameters)) {
            return null;
        }

        int variableOffset = extractBreakReportVariableOffset(parameters);
        return new OnErrorBreakContext(variableOffset, parameters.reportParams().isEmpty(), false);
    }

    private int extractBreakReportVariableOffset(SubqueryCall.InTransactionsParameters parameters) {
        var variableName = parameters
                .reportParams()
                .map(reportParameters -> reportParameters.reportAs().name())
                .getOrElse(Fragment.Apply$.MODULE$::REPORT_VARIABLE);

        List<String> columns = asJava(innerFragment.outputColumns());
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equals(variableName)) {
                return i;
            }
        }

        throw new IllegalStateException("Report variable not found among columns: " + columns);
    }

    FragmentResult run(Record argument) {
        FragmentResult input = fragmentExecutor().run(callInTransactions.input(), argument);
        return new CallInTxFragmentResult(input);
    }

    private int batchSize() {
        return callInTransactions
                .inTransactionsParameters()
                .flatMap(SubqueryCall.InTransactionsParameters::batchParams)
                .map(SubqueryCall.InTransactionsBatchParameters::batchSize)
                .map(expression -> {
                    if (expression instanceof SignedDecimalIntegerLiteral literal) {
                        return literal.value().intValue();
                    }

                    if (expression instanceof ExplicitParameter parameter) {
                        return batchSizeFromParam(parameter);
                    }

                    throw new IllegalArgumentException("Unexpected batch size expression: " + expression);
                })
                .getOrElse(() -> (int) TransactionForeach$.MODULE$.defaultBatchSize());
    }

    private int batchSizeFromParam(ExplicitParameter parameter) {
        AnyValue paramValue = queryParams().get(parameter.name());
        if (paramValue instanceof LongValue longValue) {
            return (int) longValue.value();
        }

        if (paramValue instanceof IntegralValue integralValue) {
            return integralValue.intValue();
        }

        if (paramValue instanceof NoValue) {
            throw ParameterNotFoundException.expectedParam(
                    parameter.name(), queryParams().keySet());
        }

        // Parameter types are checked by semantic analysis, so this is here
        // only in case a wrong param can somehow sneak past the semantic analysis.
        throw FabricException.internalError(
                // The semantic analysis check uses this status code,
                // so let's do the same even thought SyntaxError is a bit weird
                // for this case.
                CallInTransactionsExecutor.class.getSimpleName(),
                Status.Statement.SyntaxError,
                "Type mismatch for parameter '%s': expected Integer but was %s",
                parameter.name(),
                paramValue.getTypeName());
    }

    private FragmentResult processInputRecord(Record argument) {
        if (onErrorBreakContext != null && onErrorBreakContext.breakExecution) {
            return produceBreakOutput(argument);
        }

        PrepareResult prepareResult = prepare(innerFragment, argument);

        if (batchGraph == null) {
            batchGraph = prepareResult.graphWithNotification().graph();
            batchTransactionMode = prepareResult.transactionMode();
        }

        List<NotificationImplementation> notifications = new ArrayList<>();
        if (prepareResult.graphWithNotification().notification().isDefined()) {
            notifications.add(
                    prepareResult.graphWithNotification().notification().get());
        }
        if (!batchGraph.equals(prepareResult.graphWithNotification().graph())) {
            FragmentResult result = processBufferedInputRows(notifications);
            batchGraph = prepareResult.graphWithNotification().graph();
            batchTransactionMode = prepareResult.transactionMode();
            inputRowsBuffer.add(new BufferedInputRow(prepareResult.argumentValues(), argument));
            return result;
        }

        inputRowsBuffer.add(new BufferedInputRow(prepareResult.argumentValues(), argument));
        if (inputRowsBuffer.size() == batchSize) {
            return processBufferedInputRows(notifications);
        }

        return StatementResults.emptyFragment();
    }

    private FragmentResult produceBreakOutput(Record argument) {
        List<String> columns = asJava(innerFragment.outputColumns());
        int columnCount = columns.size() - addedColumnsCount();
        List<AnyValue> values = new ArrayList<>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            if (i == onErrorBreakContext.reportVariableOffset) {
                MapValueBuilder builder = new MapValueBuilder(4);
                builder.add("started", BooleanValue.FALSE);
                builder.add("committed", BooleanValue.FALSE);
                builder.add("transactionId", NoValue.NO_VALUE);
                builder.add("errorMessage", NoValue.NO_VALUE);
                values.add(builder.build());
            } else {
                values.add(NoValue.NO_VALUE);
            }
        }

        var record = Records.join(argument, Records.of(values));
        return StatementResults.oneRecord(columns, record, resultExecutionType);
    }

    private FragmentResult processBufferedInputRows(List<NotificationImplementation> notifications) {
        if (inputRowsBuffer.isEmpty()) {
            return StatementResults.emptyFragment();
        }

        MapValue params = addParamsFromInputRows(batchGraph.name().name());

        var result = doExecuteFragment(
                innerFragment,
                params,
                batchGraph,
                batchTransactionMode,
                // Inner part of CALL IN TRANSACTIONS does not have a child plan node
                // Unlike standard query execution with which most logic is shared
                StatementResults::emptyFragment,
                notifications);
        var inputRecords = new ArrayList<>(inputRowsBuffer);
        var adjustedResult = new DelegatingFragmentResult(result) {

            @Override
            public Record next() {
                var outputRecord = super.next();
                if (outputRecord == null) {
                    return null;
                }

                if (onErrorBreakContext != null) {
                    outputRecord = checkBreakCondition(outputRecord);
                }

                if (callInTransactions.outputColumns().isEmpty()) {
                    return getMatchingInputRecord(outputRecord, inputRecords);
                } else {
                    return Records.join(
                            getMatchingInputRecord(outputRecord, inputRecords), stripAddedColumns(outputRecord));
                }
            }
        };

        batchGraph = null;
        batchTransactionMode = null;
        inputRowsBuffer.clear();
        return adjustedResult;
    }

    private Record getMatchingInputRecord(Record outputRecord, List<BufferedInputRow> inputRecords) {
        // We are using the knowledge that Stitcher adds this always as the last column.
        var rowIdColumn = innerFragment.outputColumns().size() - 1;
        var rowId = (IntegralValue) outputRecord.getValue(rowIdColumn);
        var rowIdAsInt = rowId instanceof LongValue lv ? (int) lv.value() : rowId.intValue();
        return inputRecords.get(rowIdAsInt).record;
    }

    private Record stripAddedColumns(Record record) {
        // Stitcher adds 1-2 columns always as the last columns
        int columnCount = record.size() - addedColumnsCount();
        AnyValue[] values = new AnyValue[columnCount];
        // We are using the knowledge that Stitcher adds Row ID column always as the last column.
        for (int i = 0; i < columnCount; i++) {
            values[i] = record.getValue(i);
        }

        return Records.of(values);
    }

    private int addedColumnsCount() {
        int addedColumnsCount = 1;
        if (onErrorBreakContext != null && onErrorBreakContext.reportVariableAdded) {
            addedColumnsCount++;
        }

        return addedColumnsCount;
    }

    private Record checkBreakCondition(Record outputRecord) {
        var value = outputRecord.getValue(onErrorBreakContext.reportVariableOffset);
        var mapValue = (MapValue) value;
        if (mapValue.get("errorMessage") != NoValue.NO_VALUE) {
            onErrorBreakContext = new OnErrorBreakContext(
                    onErrorBreakContext.reportVariableOffset, onErrorBreakContext.reportVariableAdded, true);
        }

        return outputRecord;
    }

    private MapValue addParamsFromInputRows(String graphName) {
        List<String> bindings = asJava(innerFragment.argumentColumns());

        var rowListBuilder = ListValueBuilder.newListBuilder(inputRowsBuffer.size());
        for (int i = 0; i < inputRowsBuffer.size(); i++) {
            MapValue rowParams = rowToParams(inputRowsBuffer.get(i), bindings, i, graphName);
            rowListBuilder.add(rowParams);
        }
        var rows = rowListBuilder.build();

        MapValueBuilder builder = new MapValueBuilder(queryParams().size() + 1);
        queryParams().foreach(builder::add);
        builder.add(Fragment.Apply$.MODULE$.CALL_IN_TX_ROWS(), rows);
        return builder.build();
    }

    private MapValue rowToParams(BufferedInputRow inputRow, List<String> bindings, int rowId, String graphName) {
        MapValueBuilder builder = new MapValueBuilder(bindings.size() + 1);
        bindings.forEach(
                var -> builder.add(var, validateValue(inputRow.argumentValues().get(var), var, graphName)));
        builder.add(Fragment.Apply$.MODULE$.CALL_IN_TX_ROW_ID(), Values.intValue(rowId));
        return builder.build();
    }

    @Override
    FragmentResult runRemote(
            Location.Remote location,
            ExecutionOptions options,
            String query,
            TransactionMode transactionMode,
            MapValue params) {
        var profilingFragment = profilingContext.fragmentStart(location, query);
        var result = ctx().getRemote().runInAutocommitTransaction(location, options, query, transactionMode, params);
        return StatementResults.toFragmentResult(result, profilingFragment);
    }

    @Override
    FragmentResult runLocal(
            Location.Local location,
            TransactionMode transactionMode,
            QueryStatementLifecycles.StatementLifecycle parentLifecycle,
            FullyParsedQuery query,
            MapValue params,
            FragmentResult input,
            ExecutionOptions executionOptions,
            Boolean targetsComposite) {
        var queryInput = new QueryInput() {

            @Override
            public Record next() {
                return input.next();
            }

            @Override
            public void consume() {
                input.consume();
            }
        };

        var profilingFragment = profilingContext.fragmentStart(location, query.description());
        var result = ctx().getLocal()
                .runInAutocommitTransaction(location, parentLifecycle, query, params, queryInput, executionOptions);
        return StatementResults.toFragmentResult(result, profilingFragment);
    }

    private class CallInTxFragmentResult implements FragmentResult {

        private FragmentResult currentBatch;
        private final FragmentResult input;
        private final List<Supplier<PlanlessSummary>> summaries = new ArrayList<>();
        private boolean inputExhausted = false;

        private CallInTxFragmentResult(FragmentResult input) {
            this.input = input;
            summaries.add(input::consume);
        }

        @Override
        public List<String> columns() {
            return asJava(callInTransactions.outputColumns());
        }

        @Override
        public Record next() {
            if (currentBatch != null) {
                Record resultRecord = currentBatch.next();
                if (resultRecord != null || inputExhausted) {
                    return resultRecord;
                } else {
                    currentBatch = null;
                }
            }

            Record inputRecord = input.next();
            if (inputRecord == null) {
                currentBatch = processBufferedInputRows(new ArrayList<>());
                inputExhausted = true;
            } else {
                currentBatch = processInputRecord(inputRecord);
            }

            summaries.add(currentBatch::consume);
            // We have just produced a new batch, so let's go again.
            return next();
        }

        @Override
        public PlanlessSummary consume() {
            return summaries.stream()
                    .map(Supplier::get)
                    .reduce(PlanlessSummary::merge)
                    .orElse(null);
        }

        @Override
        public QueryExecutionType executionType() {
            return resultExecutionType;
        }
    }

    private record BufferedInputRow(Map<String, AnyValue> argumentValues, Record record) {}

    private record OnErrorBreakContext(int reportVariableOffset, boolean reportVariableAdded, boolean breakExecution) {}
}
