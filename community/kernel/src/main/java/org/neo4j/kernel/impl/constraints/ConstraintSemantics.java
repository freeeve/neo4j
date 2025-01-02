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
package org.neo4j.kernel.impl.constraints;

import org.neo4j.annotations.service.Service;
import org.neo4j.service.PrioritizedService;
import org.neo4j.service.Services;
import org.neo4j.storageengine.api.ConstraintRuleAccessor;

/**
 * Implements semantics of constraint creation and enforcement.
 */
@Service
public abstract class ConstraintSemantics implements PrioritizedService, ConstraintValidator, ConstraintRuleAccessor {
    private final int priority;

    public static ConstraintSemantics getConstraintSemantics() {
        return ConstraintSemanticsProviderHolder.CONSTRAINT_SEMANTICS;
    }

    protected ConstraintSemantics(int priority) {
        this.priority = priority;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    static final class ConstraintSemanticsProviderHolder {
        private static final ConstraintSemantics CONSTRAINT_SEMANTICS = loadConstraintProvider();

        private static ConstraintSemantics loadConstraintProvider() {
            return Services.loadByPriority(ConstraintSemantics.class)
                    .orElseThrow(() ->
                            new IllegalStateException("Failed to load any instance of " + ConstraintSemantics.class));
        }
    }
}
