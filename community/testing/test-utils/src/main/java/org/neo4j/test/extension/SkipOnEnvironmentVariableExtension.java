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
package org.neo4j.test.extension;

import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SkipOnEnvironmentVariableExtension implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        var envVariable = findAnnotation(context.getElement(), SkipOnEnvironmentVariable.class)
                .flatMap(a -> ofNullable(a.envVariable()).filter(not(String::isEmpty)));
        if (envVariable.isPresent() && System.getenv().containsKey(envVariable.get())) {
            return ConditionEvaluationResult.disabled(
                    "Test is disabled because you are running with " + envVariable.get());
        }

        return ConditionEvaluationResult.enabled("Test is enabled");
    }
}
