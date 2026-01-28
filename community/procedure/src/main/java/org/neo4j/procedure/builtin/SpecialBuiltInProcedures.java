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
package org.neo4j.procedure.builtin;

import java.lang.management.ManagementFactory;
import java.util.List;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.kernel.api.procedure.CallableProcedure;
import org.neo4j.kernel.api.procedure.GlobalProcedures;

/**
 * This class houses built-in procedures which use a backdoor to inject dependencies.
 * <p>
 * TODO: The dependencies should be made available by a standard mechanism so the backdoor is not needed.
 */
public class SpecialBuiltInProcedures {

    private final List<CallableProcedure> builtins;

    private SpecialBuiltInProcedures(List<CallableProcedure> builtins) {
        this.builtins = builtins;
    }

    public void install(GlobalProcedures globalProcedures) throws ProcedureException {
        for (CallableProcedure builtin : builtins) {
            globalProcedures.register(builtin);
        }
    }

    public static SpecialBuiltInProcedures get() {
        return new SpecialBuiltInProcedures(List.of(
                new ListComponentsProcedure(new QualifiedName("dbms", "components")),
                new JmxQueryProcedure(
                        new QualifiedName("dbms", "queryJmx"), ManagementFactory.getPlatformMBeanServer())));
    }
}
