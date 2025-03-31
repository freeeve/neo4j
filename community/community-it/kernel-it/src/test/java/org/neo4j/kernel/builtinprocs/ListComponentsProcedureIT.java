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
package org.neo4j.kernel.builtinprocs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.builtin.ListComponentsProcedure;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.DbmsExtension;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.Inject;

@DbmsExtension(configurationCallback = "configure")
public class ListComponentsProcedureIT {

    @Inject
    private GraphDatabaseAPI databaseAPI;

    private final String kernelVersion = "5.27.0";

    @ExtensionCallback
    void configure(TestDatabaseManagementServiceBuilder builder) {
        builder.setConfig(GraphDatabaseInternalSettings.custom_kernel_version, kernelVersion);
    }

    @Test
    void shouldReturnConfiguredKernelVersion() {
        List<Map<String, Object>> components;
        try (var tx = databaseAPI.beginTx()) {
            var result = tx.execute("CALL dbms.components()");
            components = result.stream().toList();
            tx.commit();
        }
        assertThat(components).isNotEmpty();
        var kernelComponent =
                components.stream().filter(this::isKernelComponent).findAny();
        assertThat(kernelComponent).isPresent();
        var version = kernelComponent.get().get(ListComponentsProcedure.VERSIONS_COLUMN);
        assertThat(version).isEqualTo(List.of(kernelVersion));
    }

    private boolean isKernelComponent(Map<String, Object> component) {
        return component.get(ListComponentsProcedure.NAME_COLUMN).equals(ListComponentsProcedure.KERNEL_COMPONENT_NAME);
    }
}
