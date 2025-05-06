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

import static org.neo4j.internal.helpers.collection.Iterators.asRawIterator;
import static org.neo4j.internal.kernel.api.procs.Neo4jTypes.NTList;
import static org.neo4j.internal.kernel.api.procs.Neo4jTypes.NTString;
import static org.neo4j.internal.kernel.api.procs.ProcedureSignature.procedureSignature;
import static org.neo4j.values.storable.Values.EMPTY_STRING;
import static org.neo4j.values.storable.Values.stringValue;
import static org.neo4j.values.storable.Values.utf8Value;

import java.util.ArrayList;
import java.util.List;
import org.neo4j.collection.ResourceRawIterator;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.kernel.api.ResourceMonitor;
import org.neo4j.kernel.api.procedure.CallableProcedure;
import org.neo4j.kernel.api.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.virtual.ListValue;
import org.neo4j.values.virtual.VirtualValues;

/**
 * This procedure lists "components" and their version. While components are currently hard-coded, it is intended that this implementation will be replaced once
 * a clean system for component assembly exists where we could dynamically get a list of which components are loaded and what versions of them.
 * <p>
 * This way, it works as a general mechanism into which capabilities a given Neo4j system has, and which version of those components are in use.
 * <p>
 * This would include things like Kernel, Storage Engine, Query Engines, Bolt protocol versions et cetera.
 * <p>
 * The versions returned from `dbms.components()` for the "Neo4j Kernel" component depend on several factors
 * <p>
 * 1. If a value has been explicitly set for the `internal.dbms.custom_kernel_version` configuration then we return that.
 * <p>
 * 2. Else, if a custom system property `internal.neo4j.custom.version` has been set, then we return that.
 * <p>
 * 3. Else, we return the current version.
 */
public class ListComponentsProcedure extends CallableProcedure.BasicProcedure {
    private static final String SKIP_CYPHER_VERSION = "internal.components.cypher.skip";

    public static final String NAME_COLUMN = "name";
    public static final String VERSIONS_COLUMN = "versions";
    public static final String EDITION_COLUMN = "edition";
    public static final String KERNEL_COMPONENT_NAME = "Neo4j Kernel";

    private static final TextValue NEO4J_KERNEL = utf8Value(KERNEL_COMPONENT_NAME);
    private static final TextValue CYPHER = utf8Value("Cypher");
    private static final ListValue cypherVersions = VirtualValues.list(stringValue("5"));
    private final List<AnyValue[]> components;

    public ListComponentsProcedure(
            QualifiedName name, String neo4jVersion, String neo4jEdition, String customVersionConfig) {
        super(procedureSignature(name)
                .out(NAME_COLUMN, NTString, "The name of the component.")
                // Since Bolt, Cypher and other components support multiple versions
                // at the same time, list of versions rather than single version.
                .out(VERSIONS_COLUMN, NTList(NTString), "The installed versions of the component.")
                .out(EDITION_COLUMN, NTString, "The Neo4j edition of the DBMS.")
                .mode(Mode.DBMS)
                .description("List DBMS components and their versions.")
                .systemProcedure()
                .build());
        this.components = createComponentsList(neo4jVersion, customVersionConfig, neo4jEdition);
    }

    @Override
    public ResourceRawIterator<AnyValue[], ProcedureException> apply(
            Context ctx, AnyValue[] input, ResourceMonitor resourceMonitor) throws ProcedureException {
        return asRawIterator(components);
    }

    private static List<AnyValue[]> createComponentsList(
            String neo4jVersion, String customVersionConfig, String neo4jEdition) {
        var version = customVersionConfig != null ? stringValue(customVersionConfig) : stringValue(neo4jVersion);
        var edition = stringValue(neo4jEdition);

        List<AnyValue[]> components = new ArrayList<>(2);
        components.add(new AnyValue[] {NEO4J_KERNEL, VirtualValues.list(version), edition});
        if (Boolean.getBoolean(SKIP_CYPHER_VERSION)) {
            return components;
        }
        components.add(new AnyValue[] {CYPHER, cypherVersions, EMPTY_STRING});
        return components;
    }
}
