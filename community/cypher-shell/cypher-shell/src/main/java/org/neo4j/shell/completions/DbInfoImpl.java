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
package org.neo4j.shell.completions;

import static org.neo4j.shell.util.Versions.version;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.driver.Value;
import org.neo4j.shell.log.Logger;
import org.neo4j.shell.parameter.ParameterService;
import org.neo4j.shell.state.BoltStateHandler;
import org.neo4j.shell.util.Versions;

public class DbInfoImpl extends DbInfo {
    private static final Logger log = Logger.create();
    final BoltStateHandler boltStateHandler;
    final boolean completionsEnabledByConfig;
    QueryPoller queryPoller;

    private void initializeQueryPoller() {
        this.queryPoller = new QueryPoller(boltStateHandler);
        var fetchDataSummary = new QueryPoller.PollingQuery(QueryPoller.FETCH_DATA_SUMMARY, records -> {
            this.labels = records.get(0).get("result").asList(Value::asString);
            this.relationshipTypes = records.get(1).get("result").asList(Value::asString);
            this.propertyKeys = records.get(2).get("result").asList(Value::asString);
        });
        var fetchDatabases = new QueryPoller.PollingQuery(QueryPoller.FETCH_DATABASES, records -> {
            this.databaseNames =
                    records.stream().map(r -> r.get("name").asString()).toList();
            this.aliasNames = records.stream()
                    .flatMap(r -> r.get("aliases").asList(Value::toString).stream()
                            .map(alias -> {
                                if (alias.startsWith("\"") && alias.endsWith("\"")) {
                                    return alias.substring(1, alias.length() - 1);
                                } else {
                                    return alias;
                                }
                            }))
                    .toList();
            var currentDb = boltStateHandler.connectionConfig().database();
            records.forEach(r -> {
                var dbName = r.get("name").asString();
                if (Objects.equals(dbName, currentDb)) {
                    String defaultVersion = r.get("defaultLanguage").asString();
                    Arrays.asList(CypherVersion.values()).forEach(v -> {
                        if (defaultVersion.equals(v.description)) {
                            defaultLanguage = v;
                        }
                    });
                }
            });
        });

        Arrays.asList(CypherVersion.values()).forEach(v -> {
            this.procedures.put(v, new ConcurrentHashMap<>());
            this.functions.put(v, List.of());
        });

        var fetchProcedures = Arrays.stream(CypherVersion.values())
                .map(this::getFetchProcedures)
                .toList();

        var fetchProceduresLegacy = getFetchProcedures(null);

        var fetchFunctions = Arrays.stream(CypherVersion.values())
                .map(this::getFetchFunctions)
                .toList();

        var fetchFunctionsLegacy = getFetchFunctions(null);
        var fetchRoles = new QueryPoller.PollingQuery(QueryPoller.FETCH_ROLES, records -> {
            this.roleNames = records.stream().map(r -> r.get("role").asString()).toList();
        });
        var fetchUsers = new QueryPoller.PollingQuery(QueryPoller.FETCH_USERS, records -> {
            this.userNames = records.stream().map(r -> r.get("user").asString()).toList();
        });

        var legacyFunctionAndProcedurePolling = List.of(fetchProceduresLegacy, fetchFunctionsLegacy);
        var versionedFunctionAndProcedurePolling =
                Stream.concat(fetchProcedures.stream(), fetchFunctions.stream()).toList();

        queryPoller.startPolling(
                fetchDatabases,
                legacyFunctionAndProcedurePolling,
                versionedFunctionAndProcedurePolling,
                fetchDataSummary,
                fetchRoles,
                fetchUsers);
    }

    private QueryPoller.PollingQuery getFetchProcedures(CypherVersion version) {
        var parserPrepend = version != null ? version.description + " " : "";
        var resolvedCypherVersion = version != null ? version : CypherVersion.Cypher5;
        return new QueryPoller.PollingQuery(parserPrepend + QueryPoller.FETCH_PROCEDURES, records -> {
            this.procedures.keySet().forEach(v -> this.procedures.put(v, new ConcurrentHashMap<>()));
            for (var record : records) {
                var procedureName = record.get("name").asString();
                var returnDescription = record.get("returnDescription")
                        .asList(x -> new ReturnDescription(x.get("name").asString()));
                this.procedures.get(resolvedCypherVersion).put(procedureName, new Neo4jProcedure(returnDescription));
            }
        });
    }

    private QueryPoller.PollingQuery getFetchFunctions(CypherVersion version) {
        var parserPrepend = version != null ? version.description + " " : "";
        var resolvedCypherVersion = version != null ? version : CypherVersion.Cypher5;
        return new QueryPoller.PollingQuery(parserPrepend + QueryPoller.FETCH_FUNCTIONS, records -> {
            this.functions.put(
                    resolvedCypherVersion,
                    records.stream().map(r -> r.get("name").asString()).toList());
        });
    }

    public DbInfoImpl(
            ParameterService parameterService, BoltStateHandler boltStateHandler, boolean completionsEnabledByConfig) {
        super(parameterService);
        this.completionsEnabledByConfig = completionsEnabledByConfig;
        this.boltStateHandler = boltStateHandler;
        if (completionsEnabled()) {
            initializeQueryPoller();
        }
    }

    @Override
    public boolean completionsEnabled() {
        if (completionsEnabledByConfig
                && versionCompatibleWithCompletions.isEmpty()
                && boltStateHandler.isConnected()) {
            try {
                var serverVersion = version(boltStateHandler.getServerVersion());
                var enableCompletions = serverVersion.compareTo(version("5.0.0")) >= 0;
                versionCompatibleWithCompletions = Optional.of(enableCompletions);
            } catch (Versions.FailedToParseException e) {
                log.warn("Failed to parse server version", e);
            }
        }

        return versionCompatibleWithCompletions.orElse(false);
    }

    @Override
    public void resumePolling() {
        if (queryPoller != null) {
            queryPoller.resumePolling();
        } else if (completionsEnabled()) {
            initializeQueryPoller();
        }
    }

    @Override
    public void stopPolling() {
        if (queryPoller != null) {
            queryPoller.stopPolling();
        }
    }

    @Override
    public void close() throws Exception {
        cleanDbInfo();
        if (queryPoller != null) {
            queryPoller.close();
            queryPoller = null;
        }
    }
}
