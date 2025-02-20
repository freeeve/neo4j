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
package org.neo4j.internal.kernel.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.neo4j.internal.kernel.api.security.PatternSegment.RelNullPatternSegment;
import static org.neo4j.internal.kernel.api.security.PropertyRule.NullOperator;

import java.util.Set;
import org.junit.jupiter.api.Test;

public class RelNullPatternSegmentTest {

    @Test
    void testPatternNoType() {
        var rps = new RelNullPatternSegment("p", NullOperator.IS_NULL);
        var rpsSpy = spy(rps);
        when(rpsSpy.propertyString("r")).thenReturn("propertyString");
        assertThat(rpsSpy.pattern()).isEqualTo("()-[r]-() WHERE propertyString IS NULL");
    }

    void testPatternOneType() {
        var rps = new RelNullPatternSegment(Set.of("R"), "p", NullOperator.IS_NULL);
        var rpsSpy = spy(rps);
        when(rpsSpy.propertyString("r")).thenReturn("propertyString");
        assertThat(rpsSpy.pattern()).isEqualTo("()-[r:R]-() WHERE propertyString IS NULL");
    }

    void testPatternTwoTypes() {
        var rps = new RelNullPatternSegment(Set.of("R1", "R2"), "p", NullOperator.IS_NULL);
        var rpsSpy = spy(rps);
        when(rpsSpy.propertyString("r")).thenReturn("propertyString");
        assertThat(rpsSpy.pattern()).isEqualTo("()-[r:R1|R2]-() WHERE propertyString IS NULL");
    }

    @Test
    void testToString() {
        var rps = new RelNullPatternSegment("propertyString", NullOperator.IS_NULL);
        var rpsSpy = spy(rps);
        when(rpsSpy.pattern()).thenReturn("patternString");
        assertThat(rpsSpy.toString()).isEqualTo("FOR(patternString)");
    }

    @Test
    void testConstructorDisallowsNullParameters() {
        assertThatThrownBy(() -> new RelNullPatternSegment(null, "p1", NullOperator.IS_NULL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("elementTypes must not be null");
        assertThatThrownBy(() -> new RelNullPatternSegment(null, NullOperator.IS_NULL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("property must not be null");
        assertThatThrownBy(() -> new RelNullPatternSegment(Set.of("R1"), null, NullOperator.IS_NULL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("property must not be null");
    }

    @Test
    void testGetRelationshipType() {
        var rps1 = new RelNullPatternSegment("p1", NullOperator.IS_NULL);
        var rps2 = new RelNullPatternSegment(Set.of("R1"), "p1", NullOperator.IS_NULL);
        var rps3 = new RelNullPatternSegment(Set.of("R1", "R2"), "p1", NullOperator.IS_NULL);
        assertThat(rps1.elementTypes()).isEmpty();
        assertThat(rps2.elementTypes()).containsExactlyInAnyOrder("R1");
        assertThat(rps3.elementTypes()).containsExactlyInAnyOrder("R1", "R2");
    }
}
