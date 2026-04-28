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
package org.neo4j.kernel.impl.index.schema;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.internal.schema.IndexPrototype.forSchema;
import static org.neo4j.internal.schema.SchemaDescriptors.forLabel;

import org.junit.jupiter.api.Test;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotApplicableKernelException;
import org.neo4j.internal.schema.AllIndexProviderDescriptors;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.token.api.TokenConstants;
import org.neo4j.values.storable.Values;

class RangeIndexReaderValidateCompositeQueryTest {
    private static final int LABEL = 42;
    private static final int PROP_A = 100;
    private static final int PROP_B = 200;
    private static final int PROP_C = 300;

    private static final IndexDescriptor COMPOSITE_AB = forSchema(forLabel(LABEL, PROP_A, PROP_B))
            .withIndexType(IndexType.RANGE)
            .withIndexProvider(AllIndexProviderDescriptors.RANGE_DESCRIPTOR)
            .withName("composite_ab")
            .materialise(1);

    private static final IndexDescriptor COMPOSITE_ABC = forSchema(forLabel(LABEL, PROP_A, PROP_B, PROP_C))
            .withIndexType(IndexType.RANGE)
            .withIndexProvider(AllIndexProviderDescriptors.RANGE_DESCRIPTOR)
            .withName("composite_abc")
            .materialise(2);

    @Test
    void shouldAcceptCorrectlyOrderedPredicates() {
        PropertyIndexQuery[] twoProp = {
            PropertyIndexQuery.exact(PROP_A, Values.of("a")), PropertyIndexQuery.exact(PROP_B, Values.of("b"))
        };
        PropertyIndexQuery[] threeProp = {
            PropertyIndexQuery.exact(PROP_A, Values.of("a")),
            PropertyIndexQuery.exact(PROP_B, Values.of("b")),
            PropertyIndexQuery.exact(PROP_C, Values.of("c"))
        };

        assertThatCode(() -> RangeIndexReader.assertPredicateKeyOrder(COMPOSITE_AB, twoProp))
                .doesNotThrowAnyException();
        assertThatCode(() -> RangeIndexReader.assertPredicateKeyOrder(COMPOSITE_ABC, threeProp))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectSwappedOrderPredicates() {
        PropertyIndexQuery[] predicates = {
            PropertyIndexQuery.exact(PROP_B, Values.of("b")), PropertyIndexQuery.exact(PROP_A, Values.of("a"))
        };

        assertThatThrownBy(() -> RangeIndexReader.assertPredicateKeyOrder(COMPOSITE_AB, predicates))
                .isInstanceOf(IndexNotApplicableKernelException.class)
                .hasMessageContaining("position 0")
                .hasMessageContaining(Integer.toString(PROP_A))
                .hasMessageContaining(Integer.toString(PROP_B));
    }

    @Test
    void shouldRejectTooFewPredicates() {
        PropertyIndexQuery[] predicates = {PropertyIndexQuery.exact(PROP_A, Values.of("a"))};

        assertThatThrownBy(() -> RangeIndexReader.assertPredicateKeyOrder(COMPOSITE_AB, predicates))
                .isInstanceOf(IndexNotApplicableKernelException.class)
                .hasMessageContaining("specifies 2 properties")
                .hasMessageContaining("1 lookup predicates");
    }

    @Test
    void shouldTolerateAllEntriesScanOnCompositeIndex() {
        PropertyIndexQuery[] predicates = {PropertyIndexQuery.allEntries()};

        assertThatCode(() -> RangeIndexReader.assertPredicateKeyOrder(COMPOSITE_AB, predicates))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldTolerateAnyPropertyKeySentinel() {
        PropertyIndexQuery[] predicates = {
            PropertyIndexQuery.exact(TokenConstants.ANY_PROPERTY_KEY, Values.of("a")),
            PropertyIndexQuery.exact(PROP_B, Values.of("b"))
        };

        assertThatCode(() -> RangeIndexReader.assertPredicateKeyOrder(COMPOSITE_AB, predicates))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTooManyPredicates() {
        PropertyIndexQuery[] predicates = {
            PropertyIndexQuery.exact(PROP_A, Values.of("a")),
            PropertyIndexQuery.exact(PROP_B, Values.of("b")),
            PropertyIndexQuery.exact(PROP_C, Values.of("c"))
        };

        assertThatThrownBy(() -> RangeIndexReader.assertPredicateKeyOrder(COMPOSITE_AB, predicates))
                .isInstanceOf(IndexNotApplicableKernelException.class)
                .hasMessageContaining("specifies 2 properties")
                .hasMessageContaining("3 lookup predicates");
    }
}
