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
package org.neo4j.fleetmanagement.common;

import static org.neo4j.internal.kernel.api.security.LoginContext.fullAccess;

import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseNotFoundException;
import org.neo4j.fleetmanagement.utils.Logger;
import org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;

public class TransactionUtil {
    private static Log getLog() {
        return Logger.getNeo4jLogger();
    }

    /**
     * This allows us to set the user-agent and automatically logs errors.
     * Errors are NOT rethrown to the caller.
     */
    public static <T> T withTransaction(
            DatabaseManagementService service, String databaseName, TransactionWork<T> work) {
        GraphDatabaseAPI db;
        try {
            db = (GraphDatabaseAPI) service.database(databaseName);
        } catch (DatabaseNotFoundException e) {
            getLog().error("Fleet manager encountered an error: Database not found: " + databaseName, e);
            return null;
        }
        return withTransactionAndErrorHandling(db, work, e -> {
            getLog().error("Fleet manager encountered an error: " + e.getMessage(), e);
            return null;
        });
    }

    /**
     * This allows us to set the user-agent and automatically logs errors.
     * Errors are NOT rethrown to the caller.
     */
    public static <T> T withSystemTransaction(DatabaseManagementService service, TransactionWork<T> work) {
        return withSystemTransactionAndErrorHandling(service, work, e -> {
            getLog().error("Fleet manager encountered an error: " + e.getMessage(), e);
            return null;
        });
    }

    /**
     * This allows us to set the user-agent and automatically logs errors.
     * Errors are NOT rethrown to the caller.
     */
    public static void withSystemTransaction(DatabaseManagementService service, VoidTransactionWork work) {
        withSystemTransactionAndErrorHandling(
                service,
                tx -> {
                    work.execute(tx);
                    return null;
                },
                e -> {
                    getLog().error("Fleet manager encountered an error: " + e.getMessage(), e);
                    return null;
                });
    }

    /**
     * This allows us to set the user-agent.
     * It does NOT automatically log the error.
     * But you can define exactly what should happen with an error.
     * Does NOT re-throw errors.
     */
    public static <T> T withTransactionAndErrorHandling(
            GraphDatabaseAPI api, TransactionWork<T> work, ExceptionWork<T> errorHandler) {
        try (org.neo4j.graphdb.Transaction tx =
                api.beginTransaction(KernelTransaction.Type.EXPLICIT, fullAccess(EFM_CONNECTION), EFM_CONNECTION)) {
            return work.execute(tx);
        } catch (Exception e) {
            return errorHandler.execute(e);
        }
    }

    /**
     * This allows us to set the user-agent.
     * It does NOT automatically log the error.
     * But you can define exactly what should happen with an error.
     * Does NOT re-throw errors.
     */
    public static <T> T withSystemTransactionAndErrorHandling(
            DatabaseManagementService service, TransactionWork<T> work, ExceptionWork<T> errorHandler) {
        GraphDatabaseAPI systemDb = (GraphDatabaseAPI) service.database("system");
        return withTransactionAndErrorHandling(systemDb, work, errorHandler);
    }

    private static final ClientConnectionInfo EFM_CONNECTION = new ClientConnectionInfo() {
        @Override
        public String asConnectionDetails() {
            return "fleet-management-session\t";
        }

        @Override
        public String protocol() {
            return "fleet-management-embedded";
        }

        @Override
        public String connectionId() {
            return null;
        }
    };
}
