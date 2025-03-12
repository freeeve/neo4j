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

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.ConstraintCreator;
import org.neo4j.graphdb.schema.ConstraintDefinition;
import org.neo4j.graphdb.schema.ConstraintType;
import org.neo4j.graphdb.schema.IndexCreator;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.IndexType;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.util.Preconditions;

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
