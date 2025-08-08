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
package org.neo4j.kernel.api.impl.index;

import org.junit.jupiter.api.BeforeAll;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.schema.IndexType;
import org.neo4j.kernel.api.index.IndexProviderApprovalTest;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.SkipOnSpd;

@SkipOnSpd(
        reason =
                "The test inserts typed empty arrays in the database using core API that can't be effectively matched using cypher."
                        + "The same thing would happen in a regular (i.e., non spd) database if you used core api to set a property p=new int[]  and then later used cypher to match p = []."
                        + "Still, SPD introduces a deviation from a regular database shown by this test because now the results from an index property are different from the non indexed property."
                        + "This happens because in a regular database we never resort to using cypher whereas in the SPD case we need to use cypher to talk with the shards",
        notes = SkipOnSpd.Note.notSupported)
public class RangeIndexIndexProviderApprovalTest extends IndexProviderApprovalTest {
    @BeforeAll
    public static void init() {
        DatabaseManagementService managementService =
                new TestDatabaseManagementServiceBuilder().impermanent().build();
        setupBeforeAllTests(managementService, IndexType.RANGE);
    }
}
