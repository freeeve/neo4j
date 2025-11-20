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
package org.neo4j.internal.batchimport.input;

import static java.lang.String.format;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.common.EntityType;
import org.neo4j.internal.batchimport.cache.idmapping.string.DuplicateInputIdException;
import org.neo4j.internal.batchimport.input.csv.Type;
import org.neo4j.util.concurrent.AsyncEvent;
import org.neo4j.util.concurrent.AsyncEvents;

public final class BadCollector implements Collector {
    public static final String BAD_FILE_NAME = "bad.log";

    /**
     * Introduced to avoid creating an exception for every reported bad thing, since it can be
     * quite the performance hogger for scenarios where there are many many bad things to collect.
     */
    abstract static class ProblemReporter extends AsyncEvent {
        private final int type;

        ProblemReporter(int type) {
            this.type = type;
        }

        int type() {
            return type;
        }

        abstract String message();

        abstract InputException exception();
    }

    interface Monitor {
        default void beforeProcessEvent() {}
    }

    static final Monitor NO_MONITOR = new Monitor() {};

    static final int BAD_RELATIONSHIPS = 0x1;
    static final int DUPLICATE_NODES = 0x2;
    static final int EXTRA_COLUMNS = 0x4;
    static final int VIOLATING_NODES = 0x8;
    static final int VIOLATING_SCHEMA = 0x10;
    static final int OTHER_NODE_VIOLATION = 0x20;
    static final int OTHER_RELATIONSHIP_VIOLATION = 0x40;
    static final int BAD_NODES = DUPLICATE_NODES | VIOLATING_NODES | OTHER_NODE_VIOLATION;

    static final int COLLECT_ALL = -1;
    public static final long UNLIMITED_TOLERANCE = -1;
    static final int DEFAULT_BACK_PRESSURE_THRESHOLD = 10_000;

    private final PrintStream out;
    private final long tolerance;
    private final int collect;
    private final int backPressureThreshold;
    private final boolean logBadEntries;
    private final Monitor monitor;

    // volatile since one importer thread calls collect(), where this value is incremented and later the "main"
    // thread calls badEntries() to get a count.
    private final AtomicLong badEntries = new AtomicLong();
    private final AsyncEvents<ProblemReporter> logger;
    private final Thread eventProcessor;
    private final AtomicLong queueSize = new AtomicLong();

    public BadCollector(OutputStream out, long tolerance, int collect) {
        this(out, tolerance, collect, DEFAULT_BACK_PRESSURE_THRESHOLD, false, NO_MONITOR);
    }

    BadCollector(
            OutputStream out,
            long tolerance,
            int collect,
            int backPressureThreshold,
            boolean skipBadEntriesLogging,
            Monitor monitor) {
        this.out = new PrintStream(out);
        this.tolerance = tolerance;
        this.collect = collect;
        this.backPressureThreshold = backPressureThreshold;
        this.logBadEntries = !skipBadEntriesLogging;
        this.monitor = monitor;
        this.logger = new AsyncEvents<>(this::processEvent, AsyncEvents.Monitor.NONE);
        this.eventProcessor = new Thread(logger);
        this.eventProcessor.start();
    }

    private void processEvent(ProblemReporter report) {
        monitor.beforeProcessEvent();
        out.println(report.message());
        queueSize.addAndGet(-1);
    }

    @Override
    public void collectBadRelationship(
            Object startId,
            Group startIdGroup,
            Object type,
            Object endId,
            Group endIdGroup,
            Object specificValue,
            String source,
            long lineNumber) {
        collect(new RelationshipsProblemReporter(
                startId, startIdGroup, type, endId, endIdGroup, specificValue, source, lineNumber));
    }

    @Override
    public void collectExtraColumns(final String source, final long row, final String value) {
        collect(new ExtraColumnsProblemReporter(row, source, value));
    }

    @Override
    public void collectDuplicateNode(Object id, long actualId, Group group, String source, long lineNumber) {
        collect(new NodesProblemReporter(id, group, source, lineNumber));
    }

    @Override
    public void collectEntityViolatingConstraint(
            Object id,
            long actualId,
            Map<String, Object> properties,
            String constraintDescription,
            EntityType entityType,
            String sourceDescription,
            long lineNumber) {
        collect(new EntityViolatingConstraintReporter(
                id, actualId, properties, constraintDescription, entityType, sourceDescription, lineNumber));
    }

    @Override
    public void collectRelationshipViolatingConstraint(
            Map<String, Object> properties,
            String constraintDescription,
            Object startId,
            Group startIdGroup,
            String type,
            Object endId,
            Group endIdGroup,
            String sourceDescription,
            long lineNumber) {
        collect(new RelationshipViolatingConstraintReporter(
                properties,
                constraintDescription,
                startId,
                startIdGroup,
                type,
                endId,
                endIdGroup,
                sourceDescription,
                lineNumber));
    }

    @Override
    public void collectSchemaCommandFailure(EntityType entityType, String failureMessage) {
        collect(new SchemaCommandFailureReporter(entityType, failureMessage));
    }

    @Override
    public void collectOtherNodeViolation(String format, Object... parameters) {
        collect(new OtherViolationReporter(EntityType.NODE, format, parameters));
    }

    @Override
    public void collectOtherRelationshipViolation(String format, Object... parameters) {
        collect(new OtherViolationReporter(EntityType.RELATIONSHIP, format, parameters));
    }

    @Override
    public boolean isCollectingBadRelationships() {
        return collects(BAD_RELATIONSHIPS);
    }

    private void collect(ProblemReporter report) {
        boolean collect = collects(report.type());
        if (collect) {
            // This type of problem is collected and we're within the max threshold, so it's OK
            long count = badEntries.incrementAndGet();
            if (tolerance == UNLIMITED_TOLERANCE || count <= tolerance) {
                // We're within the threshold
                if (logBadEntries) {
                    // Send this to the logger... but first apply some back pressure if queue is growing big
                    while (queueSize.get() >= backPressureThreshold) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                    }
                    logger.send(report);
                    queueSize.addAndGet(1);
                }
                return; // i.e. don't treat this as an exception
            }
        }

        InputException exception = report.exception();
        throw collect
                ? new InputException(
                        format(
                                "Too many bad entries %d, where last one was: %s",
                                badEntries.longValue(), exception.getMessage()),
                        exception)
                : exception;
    }

    @Override
    public void close() {
        logger.shutdown();
        try {
            logger.awaitTermination();
            eventProcessor.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            out.flush();
            out.close();
        }
    }

    @Override
    public long badEntries() {
        return badEntries.get();
    }

    private boolean collects(int bit) {
        return (collect & bit) != 0;
    }

    private static class RelationshipsProblemReporter extends ProblemReporter {
        private String message;
        private final Object specificValue;
        private final Object startId;
        private final Group startIdGroup;
        private final Object type;
        private final Object endId;
        private final Group endIdGroup;
        private final String source;
        private final long lineNumber;

        private RelationshipsProblemReporter(
                Object startId,
                Group startIdGroup,
                Object type,
                Object endId,
                Group endIdGroup,
                Object specificValue,
                String source,
                long lineNumber) {
            super(BAD_RELATIONSHIPS);
            this.startId = startId;
            this.startIdGroup = startIdGroup;
            this.type = type;
            this.endId = endId;
            this.endIdGroup = endIdGroup;
            this.specificValue = specificValue;
            this.source = source;
            this.lineNumber = lineNumber;
        }

        @Override
        public String message() {
            return getReportMessage();
        }

        @Override
        public InputException exception() {
            Optional<Type> maybeMissingDataField = getMissingDataField();
            if (maybeMissingDataField.isPresent()) {
                return new MissingRelationshipDataException(maybeMissingDataField.get(), getReportMessage(true));
            } else {
                return new InputException(getReportMessage());
            }
        }

        private String getReportMessage(boolean missingData) {
            if (message == null) {
                if (missingData) {
                    message = format(
                            "Invalid relationship in import data%n%s: line %d%n%s (%s)-[%s]->%s (%s) is missing data",
                            source, lineNumber, startId, startIdGroup, type, endId, endIdGroup);
                } else {
                    message = format(
                            "Invalid relationship in import data%n%s: line %d%n%s (%s)-[%s]->%s (%s) referring to missing node %s",
                            source, lineNumber, startId, startIdGroup, type, endId, endIdGroup, specificValue);
                }
            }
            return message;
        }

        private String getReportMessage() {
            return getReportMessage(getMissingDataField().isPresent());
        }

        // Returns the first data field that is missing, or null if none are missing
        private Optional<Type> getMissingDataField() {
            if (startId == null) {
                return Optional.of(Type.START_ID);
            } else if (endId == null) {
                return Optional.of(Type.END_ID);
            } else if (type == null) {
                return Optional.of(Type.TYPE);
            }
            return Optional.empty();
        }
    }

    private static class NodesProblemReporter extends ProblemReporter {
        private final Object id;
        private final Group group;
        private final String source;
        private final long lineNumber;

        private NodesProblemReporter(Object id, Group group, String source, long lineNumber) {
            super(DUPLICATE_NODES);
            this.id = id;
            this.group = group;
            this.source = source;
            this.lineNumber = lineNumber;
        }

        @Override
        public String message() {
            return DuplicateInputIdException.message(id, group, source, lineNumber);
        }

        @Override
        public InputException exception() {
            return new DuplicateInputIdException(id, group, source, lineNumber);
        }
    }

    private static class ExtraColumnsProblemReporter extends ProblemReporter {
        private String message;
        private final long row;
        private final String source;
        private final String value;

        private ExtraColumnsProblemReporter(long row, String source, String value) {
            super(EXTRA_COLUMNS);
            this.row = row;
            this.source = source;
            this.value = value;
        }

        @Override
        public String message() {
            return getReportMessage();
        }

        @Override
        public InputException exception() {
            return new InputException(getReportMessage());
        }

        private String getReportMessage() {
            if (message == null) {
                message =
                        format("Extra column not present in header on line %d in %s with value %s", row, source, value);
            }
            return message;
        }
    }

    private static class EntityViolatingConstraintReporter extends ProblemReporter {
        private final Object id;
        private final long actualId;
        private final Map<String, Object> properties;
        private final String constraintDescription;
        private final EntityType entityType;
        private final String sourceDescription;
        private final long lineNumber;

        private EntityViolatingConstraintReporter(
                Object id,
                long actualId,
                Map<String, Object> properties,
                String constraintDescription,
                EntityType entityType,
                String sourceDescription,
                long lineNumber) {
            super(entityType == EntityType.NODE ? VIOLATING_NODES : BAD_RELATIONSHIPS);
            this.id = id;
            this.actualId = actualId;
            this.properties = properties;
            this.constraintDescription = constraintDescription;
            this.entityType = entityType;
            this.sourceDescription = sourceDescription;
            this.lineNumber = lineNumber;
        }

        @Override
        String message() {
            return format(
                    "%s %s (internal id %d) would have violated constraint:%s with properties:%s, index:%d in '%s'",
                    entityType == EntityType.NODE ? "Node" : "Relationship",
                    id,
                    actualId,
                    constraintDescription,
                    properties,
                    lineNumber,
                    sourceDescription);
        }

        @Override
        InputException exception() {
            return new InputException(message());
        }
    }

    private static class RelationshipViolatingConstraintReporter extends ProblemReporter {
        private final Map<String, Object> properties;
        private final String constraintDescription;
        private final Object startId;
        private final Group startIdGroup;
        private final String type;
        private final Object endId;
        private final Group endIdGroup;
        private final String sourceDescription;
        private final long lineNumber;

        private RelationshipViolatingConstraintReporter(
                Map<String, Object> properties,
                String constraintDescription,
                Object startId,
                Group startIdGroup,
                String type,
                Object endId,
                Group endIdGroup,
                String sourceDescription,
                long lineNumber) {
            super(BAD_RELATIONSHIPS);
            this.properties = properties;
            this.constraintDescription = constraintDescription;
            this.startId = startId;
            this.startIdGroup = startIdGroup;
            this.type = type;
            this.endId = endId;
            this.endIdGroup = endIdGroup;
            this.sourceDescription = sourceDescription;
            this.lineNumber = lineNumber;
        }

        @Override
        String message() {
            return format(
                    "%s%s-[%s]->%s%s would have violated constraint:%s with properties:%s, index:%d in '%s'",
                    startId,
                    startIdGroup != null ? " (" + startIdGroup + ")" : "",
                    type,
                    endId,
                    endIdGroup != null ? " (" + endIdGroup + ")" : "",
                    constraintDescription,
                    properties,
                    lineNumber,
                    sourceDescription);
        }

        @Override
        InputException exception() {
            return new InputException(message());
        }
    }

    private static class SchemaCommandFailureReporter extends ProblemReporter {

        private static final int ALL_SCHEMA_VIOLATIONS = VIOLATING_SCHEMA | BAD_NODES | BAD_RELATIONSHIPS;
        private static final int NODE_SCHEMA_VIOLATIONS = VIOLATING_SCHEMA | BAD_NODES;
        private static final int REL_SCHEMA_VIOLATIONS = VIOLATING_SCHEMA | BAD_RELATIONSHIPS;

        private final String failureMessage;

        private SchemaCommandFailureReporter(EntityType entityType, String failureMessage) {
            super(violationType(entityType));
            this.failureMessage = failureMessage;
        }

        private static int violationType(EntityType entityType) {
            if (entityType == null) {
                // just collect them all in this case as we don't know which one it was
                return ALL_SCHEMA_VIOLATIONS;
            }

            return entityType == EntityType.NODE ? NODE_SCHEMA_VIOLATIONS : REL_SCHEMA_VIOLATIONS;
        }

        @Override
        String message() {
            return failureMessage;
        }

        @Override
        InputException exception() {
            return new InputException(message());
        }
    }

    private static class OtherViolationReporter extends ProblemReporter {
        private final String format;
        private final Object[] parameters;

        public OtherViolationReporter(EntityType entityType, String format, Object[] parameters) {
            super(entityType == EntityType.NODE ? OTHER_NODE_VIOLATION : OTHER_RELATIONSHIP_VIOLATION);
            this.format = format;
            this.parameters = parameters;
        }

        @Override
        String message() {
            return format(format, parameters);
        }

        @Override
        InputException exception() {
            return new InputException(message());
        }
    }
}
