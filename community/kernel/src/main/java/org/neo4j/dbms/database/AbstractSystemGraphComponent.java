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
package org.neo4j.dbms.database;

import static org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME;
import static org.neo4j.dbms.database.SystemGraphComponent.executeWithFullAccess;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_NAME_LABEL;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DISPLAY_NAME_CONSTRAINT;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DISPLAY_NAME_PROPERTY;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.NAME_PROPERTY;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.exceptions.UpgradeException;
import org.neo4j.graphdb.ConstraintViolationException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.ConstraintCreator;
import org.neo4j.graphdb.schema.ConstraintDefinition;
import org.neo4j.graphdb.schema.ConstraintType;
import org.neo4j.graphdb.schema.IndexCreator;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.IndexType;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.helpers.collection.Pair;
import org.neo4j.util.Preconditions;
import org.neo4j.util.VisibleForTesting;

/**
 * Common code for all system graph components, apart from test implementations and the central collection class {@link SystemGraphComponents}.
 */
public abstract class AbstractSystemGraphComponent implements SystemGraphComponent {
    protected final Config config;

    public AbstractSystemGraphComponent(Config config) {
        this.config = config;
    }

    protected void initializeSystemGraphSchema(GraphDatabaseService system) throws Exception {}

    protected void initializeSystemGraphModel(Transaction tx, GraphDatabaseService systemDb) throws Exception {}

    protected void verifySystemGraph(GraphDatabaseService system) throws Exception {}

    protected void initializeSystemGraphModel(GraphDatabaseService system) throws Exception {
        try (Transaction tx = system.beginTx()) {
            initializeSystemGraphModel(tx, system);
            tx.commit();
        }
    }

    protected void postInitialization(GraphDatabaseService system, boolean wasInitialized) throws Exception {}

    @Override
    public void initializeSystemGraph(GraphDatabaseService system, boolean firstInitialization) throws Exception {
        boolean mayUpgrade = config.get(GraphDatabaseInternalSettings.automatic_upgrade_enabled);

        Preconditions.checkState(
                system.databaseName().equals(SYSTEM_DATABASE_NAME),
                "Cannot initialize system graph on database '" + system.databaseName() + "'");

        Status status = detect(system);
        if (status == Status.UNINITIALIZED) {
            initializeSystemGraphSchema(system);
            initializeSystemGraphModel(system);
            postInitialization(system, true);
        } else if (status == Status.CURRENT || (status == Status.REQUIRES_UPGRADE && !mayUpgrade)) {
            verifySystemGraph(system);
            postInitialization(system, false);
        } else if ((mayUpgrade && status == Status.REQUIRES_UPGRADE) || status == Status.UNSUPPORTED_BUT_CAN_UPGRADE) {
            upgradeToCurrent(system);
        } else {
            throw new IllegalStateException(
                    String.format("Unsupported component state for '%s': %s", componentName(), status.description()));
        }
    }

    protected static void initializeSystemGraphConstraint(
            GraphDatabaseService system, Label label, String... properties) throws Exception {
        initializeSystemGraphConstraint(system, Optional.empty(), label, properties);
    }

    protected static void initializeDisplayNameSystemGraphConstraint(GraphDatabaseService system) throws Exception {
        try {
            initializeSystemGraphConstraint(
                    system, Optional.of(DISPLAY_NAME_CONSTRAINT), DATABASE_NAME_LABEL, DISPLAY_NAME_PROPERTY);
        } catch (ConstraintViolationException e) {
            /*
             * Unlike other upgrade errors which should never occur, a ConstraintViolationException on displayName
             * can happen on upgrade in the edge case that a composite user has conflicting displayNames
             * e.g. a database or alias 'foo.bar' and simultaneously a constituent 'foo.bar' in a composite 'foo'.
             * In this case, we want to give a sensible error to the user,
             * so they can fix the naming conflict and then retry the upgrade.
             */
            Optional<Pair<String, String>> conflictingDbs = findDisplayNameConflicts(system);
            if (conflictingDbs.isPresent()) {
                throw UpgradeException.conflictingDisplayNames(
                        conflictingDbs.get().first(), conflictingDbs.get().other());
            }
            // In the unlikely event that we got an ConstraintViolationException but did not find any conflict,
            // the exception was not related to conflicting displayNames, so we just rethrow it
            throw e;
        }
    }

    @VisibleForTesting
    public static Optional<Pair<String, String>> findDisplayNameConflicts(GraphDatabaseService system) {
        Optional<Pair<String, String>> maybeConflict;
        try (var tx = system.beginTx()) {
            // Group the database names nodes by displayName
            Map<Object, List<Node>> databasesPerDisplayName = tx.findNodes(DATABASE_NAME_LABEL).stream()
                    .collect(Collectors.groupingBy(node -> node.getProperty(DISPLAY_NAME_PROPERTY)));

            maybeConflict = databasesPerDisplayName.entrySet().stream()
                    // Find all displayName conflicts (> 1 database names share the same displayName)
                    .filter(entry -> entry.getValue().size() > 1)
                    // Pick the first displayName with a conflict,
                    // sort in alphabetic displayName order to avoid flakiness in testing
                    .min(Comparator.comparing(entry -> entry.getKey().toString()))
                    // Extract the name of two conflicting databases for the error message
                    .map(entry -> {
                        List<String> conflicts = entry.getValue().stream()
                                .map(node -> node.getProperty(NAME_PROPERTY).toString())
                                .sorted() // Just needed to avoid flakiness in testing
                                .toList();
                        return Pair.of(conflicts.get(0), conflicts.get(1));
                    });

            tx.rollback();
        }
        return maybeConflict;
    }

    protected static void initializeSystemGraphConstraint(
            GraphDatabaseService system,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<String> name,
            Label label,
            String... properties)
            throws Exception {

        AtomicBoolean hasUniqueConstraint = new AtomicBoolean(false);
        executeWithFullAccess(system, tx -> hasUniqueConstraint.set(hasUniqueConstraint(tx, label, properties)));

        // Makes the creation of constraints for security idempotent
        if (!hasUniqueConstraint.get()) {
            executeWithFullAccess(system, tx -> checkForClashingIndexes(tx, label, properties));

            executeWithFullAccess(system, tx -> {
                ConstraintCreator cb = tx.schema().constraintFor(label);
                for (String prop : properties) {
                    cb = cb.assertPropertyIsUnique(prop);
                }
                if (name.isEmpty()) {
                    cb.create();
                } else {
                    cb.withName(name.get()).create();
                }
            });
        }
    }

    protected static void initialiseSystemGraphIndex(
            GraphDatabaseService system, String indexName, Label label, String... properties) throws Exception {
        executeWithFullAccess(system, tx -> {
            // Check if we already have it, but don't worry about the name since we won't replace an index
            // just because it has the wrong name
            if (!hasIndex(tx, label, properties)) {
                IndexCreator ic = tx.schema().indexFor(label).withName(indexName);
                ic.withIndexType(IndexType.RANGE);
                for (String prop : properties) {
                    ic = ic.on(prop);
                }
                ic.create();
            }
        });
    }

    private static boolean hasIndex(Transaction tx, Label label, String... properties) {
        for (IndexDefinition index : tx.schema().getIndexes(label)) {
            if (index.getPropertyKeys().equals(Arrays.asList(properties))) {
                return true;
            }
        }
        return false;
    }

    protected static boolean hasUniqueConstraint(Transaction tx, Label label, String... properties) {
        return findUniqueConstraint(tx, label, properties).isPresent();
    }

    protected static Optional<ConstraintDefinition> findUniqueConstraint(
            Transaction tx, Label label, String... properties) {
        for (ConstraintDefinition constraintDefinition : tx.schema().getConstraints(label)) {
            if (constraintDefinition.getPropertyKeys().equals(Arrays.asList(properties))
                    && constraintDefinition.isConstraintType(ConstraintType.UNIQUENESS))
                return Optional.of(constraintDefinition);
        }
        return Optional.empty();
    }

    private static void checkForClashingIndexes(Transaction tx, Label label, String... properties) {
        tx.schema().getIndexes(label).forEach(index -> {
            String[] propertyKeys = Iterables.asArray(String.class, index.getPropertyKeys());
            if (Arrays.equals(propertyKeys, properties)) {
                index.drop();
            }
        });
    }
}
