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
package org.neo4j.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.impl.transaction.stats.DatabaseTransactionStats;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.DbmsExtension;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.Inject;

@DbmsExtension(configurationCallback = "configure")
public class TransactionIdenticalValueNoopIT {
    @Inject
    private GraphDatabaseService db;

    @ExtensionCallback
    void configure(TestDatabaseManagementServiceBuilder builder) {
        builder.setConfig(GraphDatabaseInternalSettings.no_property_update_on_identical_value, true);
    }

    @Test
    void shouldNotWriteWhenSettingNodePropertyToSameValue() {
        db.executeTransactionally("CREATE (n:A {prop: 'value'})");
        var txStats = ((GraphDatabaseAPI) db)
                .getDependencyResolver()
                .resolveOptionalDependency(DatabaseTransactionStats.class)
                .get();
        long committedWriteTx = txStats.getNumberOfCommittedWriteTransactions();
        long committedReadTx = txStats.getNumberOfCommittedReadTransactions();

        db.executeTransactionally("MATCH (n:A) SET n.prop = 'value'");
        assertThat(txStats.getNumberOfCommittedWriteTransactions()).isEqualTo(committedWriteTx);
        assertThat(txStats.getNumberOfCommittedReadTransactions()).isEqualTo(committedReadTx + 1);
    }

    @Test
    void shouldNotWriteWhenSettingRelPropertyToSameValue() {
        db.executeTransactionally("CREATE ()-[:REL {prop: 'value'}]->()");
        var txStats = ((GraphDatabaseAPI) db)
                .getDependencyResolver()
                .resolveOptionalDependency(DatabaseTransactionStats.class)
                .get();
        long committedWriteTx = txStats.getNumberOfCommittedWriteTransactions();
        long committedReadTx = txStats.getNumberOfCommittedReadTransactions();

        db.executeTransactionally("MATCH ()-[r]->() SET r.prop = 'value'");
        assertThat(txStats.getNumberOfCommittedWriteTransactions()).isEqualTo(committedWriteTx);
        assertThat(txStats.getNumberOfCommittedReadTransactions()).isEqualTo(committedReadTx + 1);
    }

    @Test
    void shouldNotWriteWhenMergingNodePropertyToSameValue() {
        var txStats = ((GraphDatabaseAPI) db)
                .getDependencyResolver()
                .resolveOptionalDependency(DatabaseTransactionStats.class)
                .get();
        long committedWriteTx = txStats.getNumberOfCommittedWriteTransactions();
        long committedReadTx = txStats.getNumberOfCommittedReadTransactions();

        db.executeTransactionally("MERGE (n:B {prop: 'merge'})");
        long committedWriteTx2 = txStats.getNumberOfCommittedWriteTransactions();
        assertThat(committedWriteTx2).isGreaterThan(committedWriteTx);
        assertThat(txStats.getNumberOfCommittedReadTransactions()).isEqualTo(committedReadTx);

        db.executeTransactionally("MERGE (n:B {prop: 'merge'})");
        assertThat(txStats.getNumberOfCommittedWriteTransactions()).isEqualTo(committedWriteTx2);
        assertThat(txStats.getNumberOfCommittedReadTransactions()).isEqualTo(committedReadTx + 1);
    }

    @Test
    void shouldNotWriteWhenMergingRelPropertyToSameValue() {
        var txStats = ((GraphDatabaseAPI) db)
                .getDependencyResolver()
                .resolveOptionalDependency(DatabaseTransactionStats.class)
                .get();
        long committedWriteTx = txStats.getNumberOfCommittedWriteTransactions();
        long committedReadTx = txStats.getNumberOfCommittedReadTransactions();

        db.executeTransactionally("MERGE ()-[:TYPE {prop: 'merge'}]->()");
        long committedWriteTx2 = txStats.getNumberOfCommittedWriteTransactions();
        assertThat(committedWriteTx2).isGreaterThan(committedWriteTx);
        assertThat(txStats.getNumberOfCommittedReadTransactions()).isEqualTo(committedReadTx);

        db.executeTransactionally("MERGE ()-[:TYPE {prop: 'merge'}]->()");
        assertThat(txStats.getNumberOfCommittedWriteTransactions()).isEqualTo(committedWriteTx2);
        assertThat(txStats.getNumberOfCommittedReadTransactions()).isEqualTo(committedReadTx + 1);
    }
}
