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

import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.neo4j.tooling.procedure.compilerutils.TypeMirrorUtils;
import org.neo4j.tooling.procedure.extension.CompilationExtension;
import org.neo4j.tooling.procedure.testutils.TypeMirrorTestUtils;

@ExtendWith(CompilationExtension.class)
public class ParameterTypeVisitorTest extends TypeValidationTestSuite {

    private Types types;
    private TypeMirrorUtils typeMirrorUtils;
    private TypeMirrorTestUtils typeMirrorTestUtils;

    @BeforeEach
    public void prepare(Elements elements, Types types) {
        this.types = types;
        typeMirrorUtils = new TypeMirrorUtils(types, elements);
        typeMirrorTestUtils = new TypeMirrorTestUtils(types, elements);
    }

    @Override
    protected TypeVisitor<Boolean, Void> visitor() {
        return new ParameterTypeVisitor(types, typeMirrorUtils);
    }

    @Override
    protected TypeMirrorTestUtils typeMirrorTestUtils() {
        return typeMirrorTestUtils;
    }
}
