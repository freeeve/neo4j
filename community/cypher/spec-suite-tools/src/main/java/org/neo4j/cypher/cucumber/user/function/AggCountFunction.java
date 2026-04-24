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
package org.neo4j.cypher.cucumber.user.function;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.ThreadSafe;
import org.neo4j.procedure.UserAggregationFunction;
import org.neo4j.procedure.UserAggregationResult;
import org.neo4j.procedure.UserAggregationUpdate;

public class AggCountFunction {

    @ThreadSafe
    @UserAggregationFunction("test.aggCount")
    @Description("Aggregating Count")
    public AggregatingCountAggregator aggCount() {
        return new AggregatingCountAggregator();
    }

    public static class AggregatingCountAggregator {
        private Long count = 0L;

        @UserAggregationUpdate
        public void update(@Name("value") Object value) {
            if (value != null) {
                count++;
            }
        }

        @UserAggregationResult
        public Long result() {
            return count;
        }
    }
}
