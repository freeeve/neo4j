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
package org.neo4j.tooling.procedure.visitors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.stream.Stream;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.neo4j.tooling.procedure.extension.CompilationExtension;
import org.neo4j.tooling.procedure.messages.CompilationMessage;
import org.neo4j.tooling.procedure.testutils.ElementTestUtils;
import org.neo4j.tooling.procedure.visitors.examples.GoodContextUse;
import org.neo4j.tooling.procedure.visitors.examples.StaticNonContextMisuse;

@ExtendWith(CompilationExtension.class)
public class FieldVisitorTest {

    private ElementVisitor<Stream<CompilationMessage>, Void> fieldVisitor;
    private ElementTestUtils elementTestUtils;

    @BeforeEach
    public void prepare(Elements elements, Types types) {
        elementTestUtils = new ElementTestUtils(elements, types);
        fieldVisitor = new FieldVisitor(types, elements, true);
    }

    @Test
    public void validates_visibility_of_fields() {
        Stream<VariableElement> fields = elementTestUtils.getFields(GoodContextUse.class);

        Stream<CompilationMessage> result = fields.flatMap(fieldVisitor::visit);

        assertThat(result).isEmpty();
    }

    @Test
    public void rejects_non_static_non_context_fields() {
        Stream<VariableElement> fields = elementTestUtils.getFields(StaticNonContextMisuse.class);

        Stream<CompilationMessage> result = fields.flatMap(fieldVisitor::visit);

        assertThat(result)
                .extracting(CompilationMessage::getCategory, CompilationMessage::getContents)
                .containsExactly(tuple(Diagnostic.Kind.ERROR, "Field StaticNonContextMisuse#value should be static"));
    }
}
