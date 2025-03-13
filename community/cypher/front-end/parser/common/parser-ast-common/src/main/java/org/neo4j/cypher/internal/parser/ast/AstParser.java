/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.parser.ast;

import org.neo4j.cypher.internal.ast.Statement;
import org.neo4j.cypher.internal.ast.Statements;
import org.neo4j.cypher.internal.expressions.Expression;
import org.neo4j.cypher.internal.expressions.NumberLiteral;
import org.neo4j.cypher.internal.util.InputPosition;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.GqlHelper;
import scala.collection.immutable.ArraySeq;

/** Parses neo4j AST. */
public interface AstParser {
    Statements statements();

    Expression expression();

    NumberLiteral numberLiteral();

    ArraySeq<String> symbolicAliasName();

    default Statement singleStatement() {
        final var statements = statements();
        if (statements.size() != 1) {
            InputPosition pos = InputPosition.NONE();
            throw syntaxException(
                    GqlHelper.getGql42001_42I15(statements.size(), pos.offset(), pos.line(), pos.column()),
                    "Expected exactly one statement per query but got: %s".formatted(statements.size()));
        }
        return statements.get(0);
    }

    RuntimeException syntaxException(ErrorGqlStatusObject gqlStatusObject, String message, InputPosition position);

    private RuntimeException syntaxException(ErrorGqlStatusObject gqlStatusObject, String message) {
        return syntaxException(gqlStatusObject, message, InputPosition.NONE());
    }
}
