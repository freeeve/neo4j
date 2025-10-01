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
package org.neo4j.cypher.internal.javacompat;

import java.util.Set;
import org.neo4j.cypher.internal.frontend.phases.BaseState;
import org.neo4j.cypher.internal.notification.InternalNotification;
import org.neo4j.cypher.internal.preparser.FullyParsedQuery;
import org.neo4j.cypher.internal.preparser.PreParsedQuery;
import org.neo4j.cypher.internal.runtime.InputDataStream;
import org.neo4j.kernel.impl.query.QueryExecution;
import org.neo4j.kernel.impl.query.QueryExecutionEngine;
import org.neo4j.kernel.impl.query.QueryExecutionKernelException;
import org.neo4j.kernel.impl.query.QueryExecutionMonitor;
import org.neo4j.kernel.impl.query.QuerySubscriber;
import org.neo4j.kernel.impl.query.TransactionalContext;
import org.neo4j.values.virtual.MapValue;

/**
 * Extension of {@link QueryExecutionEngine} interface that adds low-level
 * methods used by Cypher code as opposed to {@link QueryExecutionEngine}
 * which is an API used outside Cypher modules.
 */
public interface InternalQueryExecutionEngine extends QueryExecutionEngine {

    void insertIntoCache(
            String queryText,
            PreParsedQuery preParsedQuery,
            MapValue params,
            BaseState parsedQuery,
            Set<InternalNotification> parsingNotifications);

    QueryExecution executeQuery(
            FullyParsedQuery query,
            MapValue parameters,
            TransactionalContext context,
            boolean prePopulate,
            InputDataStream input,
            QueryExecutionMonitor queryMonitor,
            QuerySubscriber subscriber)
            throws QueryExecutionKernelException;

    org.neo4j.cypher.internal.ExecutionEngine getCypherExecutionEngine();
}
