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

import java.util.Random;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.Values;

public class SeededRandFunction {

    private static Random rand = new Random(23);

    @UserFunction("test.seededRand")
    @Description("Seeded random")
    public AnyValue seededRand() {
        return Values.doubleValue(rand.nextDouble());
    }

    @UserFunction("test.setSeed")
    @Description("Set seed")
    public AnyValue setSeed(@Name(value = "seed") Long seed) {
        rand.setSeed(seed);
        return Values.longValue(seed);
    }
}
