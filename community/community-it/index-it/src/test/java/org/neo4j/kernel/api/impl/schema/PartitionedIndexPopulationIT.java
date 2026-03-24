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
package org.neo4j.kernel.api.impl.schema;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.kernel.api.impl.index.lucene.LuceneSettings;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.DbmsExtension;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.Inject;

@DbmsExtension(configurationCallback = "configure")
class PartitionedIndexPopulationIT {

    @Inject
    private GraphDatabaseService db;

    @ExtensionCallback
    void configure(TestDatabaseManagementServiceBuilder builder) {
        builder.setConfig(LuceneSettings.lucene_max_partition_size, 30_000_000);
    }

    // The partition size and the indexed data volume should lead to OOM for 2GB heap we run tests with.
    @Test
    void oom() {
        for (int i = 0; i < 1000; i++) {
            try (var tx = db.beginTx()) {
                for (int j = 0; j < 60_000; j++) {
                    var node = tx.createNode(Label.label("Number"));
                    node.setProperty("value", "" + i * 60_000 + j);
                }
                tx.commit();
            }
        }

        db.executeTransactionally("CREATE FULLTEXT INDEX numbers FOR (n:Number) ON EACH [n.value]");
        try (var tx = db.beginTx()) {
            tx.schema().awaitIndexesOnline(1, TimeUnit.HOURS);
        }
    }
}
