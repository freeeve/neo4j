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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory.ENTITY_ID_KEY;
import static org.neo4j.kernel.api.impl.schema.TextDocumentStructure.useFieldForUniquenessVerification;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriter;
import org.neo4j.values.storable.Values;

class TextDocumentStructureTest {
    @Test
    void stringWithMaximumLengthShouldBeAllowed() {
        String longestString = RandomStringUtils.randomAscii(LuceneIndexWriter.MAX_TERM_LENGTH);
        LuceneDocument document =
                LuceneDocumentsFactory.CURRENT.reusableTextDocument(123, Values.values(longestString));
        assertEquals(longestString, document.get(LuceneDocumentsFactory.textValueKey(0)));
    }

    @Test
    void shouldBuildDocumentRepresentingStringProperty() {
        // given
        LuceneDocument document = LuceneDocumentsFactory.CURRENT.reusableTextDocument(123, Values.values("hello"));

        // then
        assertEquals("123", document.get(ENTITY_ID_KEY));
        assertEquals("hello", document.get(LuceneDocumentsFactory.textValueKey(0)));
    }

    @Test
    void shouldBuildDocumentRepresentingMultipleStringProperties() {
        // given
        String[] values = new String[] {"hello", "world"};
        LuceneDocument document =
                LuceneDocumentsFactory.CURRENT.reusableTextDocument(123, Values.values((Object[]) values));

        // then
        assertEquals("123", document.get(ENTITY_ID_KEY));
        assertThat(document.get(LuceneDocumentsFactory.textValueKey(0))).isEqualTo(values[0]);
        assertThat(document.get(LuceneDocumentsFactory.textValueKey(1))).isEqualTo(values[1]);
    }

    @Test
    void checkFieldUsageForUniquenessVerification() {
        assertFalse(useFieldForUniquenessVerification("id"));
        assertFalse(useFieldForUniquenessVerification("1number"));
        assertTrue(useFieldForUniquenessVerification("number"));
        assertFalse(useFieldForUniquenessVerification("1string"));
        assertFalse(useFieldForUniquenessVerification("10string"));
        assertTrue(useFieldForUniquenessVerification("string"));
    }
}
