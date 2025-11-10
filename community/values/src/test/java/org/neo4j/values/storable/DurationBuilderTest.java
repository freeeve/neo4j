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
package org.neo4j.values.storable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.values.storable.DurationValue.build;
import static org.neo4j.values.storable.DurationValue.parse;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.gqlstatus.ErrorClassification;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.MapValueBuilder;

class DurationBuilderTest {
    @Test
    void shouldBuildDuration() {
        assertEquals(parse("P17Y"), build(asMapValue(Map.of("years", 17))));
        assertEquals(parse("P3M"), build(asMapValue(Map.of("months", 3))));
        assertEquals(parse("P18W"), build(asMapValue(Map.of("weeks", 18))));
        assertEquals(parse("P7D"), build(asMapValue(Map.of("days", 7))));
        assertEquals(parse("PT5H"), build(asMapValue(Map.of("hours", 5))));
        assertEquals(parse("PT7M"), build(asMapValue(Map.of("minutes", 7))));
        assertEquals(parse("PT2352S"), build(asMapValue(Map.of("seconds", 2352))));
        assertEquals(parse("PT0.001S"), build(asMapValue(Map.of("milliseconds", 1))));
        assertEquals(parse("PT0.000001S"), build(asMapValue(Map.of("microseconds", 1))));
        assertEquals(parse("PT0.000000001S"), build(asMapValue(Map.of("nanoseconds", 1))));
        assertEquals(
                parse("PT4.003002001S"),
                build(asMapValue(Map.of(
                        "nanoseconds", 1,
                        "microseconds", 2,
                        "milliseconds", 3,
                        "seconds", 4))));
        assertEquals(
                parse("P1Y2M3W4DT5H6M7.800000009S"),
                build(asMapValue(Map.of(
                        "years", 1,
                        "months", 2,
                        "weeks", 3,
                        "days", 4,
                        "hours", 5,
                        "minutes", 6,
                        "seconds", 7,
                        "milliseconds", 800,
                        "microseconds", -900_000,
                        "nanoseconds", 900_000_009))));
    }

    @Test
    void shouldRejectUnknownKeys() {
        assertThatThrownBy(() -> build(asMapValue(Map.of("millenia", 2))))
                .hasMessage("Unknown field: millenia")
                .asInstanceOf(type(InvalidArgumentException.class))
                .satisfies(e -> {
                    assertThat(e.gqlStatus()).isEqualTo("22G0I");
                    assertThat(e.status()).isEqualTo(Status.Data.Statement.ArgumentError);
                    assertThat(e.getClassification()).isEqualTo(ErrorClassification.CLIENT_ERROR);
                    assertThat(e.cause()).isEmpty();
                    assertThat(e.diagnosticRecord())
                            .containsExactlyInAnyOrderEntriesOf(Map.of(
                                    "CURRENT_SCHEMA",
                                    "/",
                                    "OPERATION",
                                    "",
                                    "OPERATION_CODE",
                                    "0",
                                    "_classification",
                                    "CLIENT_ERROR"));
                    assertThat(e.legacyMessage()).isEqualTo("Unknown field: millenia");
                    assertThat(e.statusDescription())
                            .isEqualTo(
                                    "error: data exception - invalid duration field. `millenia` is not a valid duration field.");
                });
    }

    @Test
    void shouldAcceptOverlapping() {
        assertEquals(parse("PT1H90M"), build(asMapValue(Map.of("hours", 1, "minutes", 90))));
        assertEquals(parse("P1DT30H"), build(asMapValue(Map.of("days", 1, "hours", 30))));
    }

    public static MapValue asMapValue(Map<String, ?> map) {
        MapValueBuilder builder = new MapValueBuilder(map.size());
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            builder.add(entry.getKey(), Values.unsafeOf(entry.getValue(), true));
        }
        return builder.build();
    }
}
