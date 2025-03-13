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

import static org.neo4j.internal.kernel.api.procs.ProcedureSignature.VOID;
import static org.neo4j.internal.kernel.api.procs.ProcedureSignature.procedureSignature;

import org.neo4j.collection.ResourceRawIterator;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.kernel.api.ResourceMonitor;
import org.neo4j.kernel.api.procedure.CallableProcedure;
import org.neo4j.kernel.api.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.ssl.config.DefaultSslPolicyProvider;
import org.neo4j.values.AnyValue;

public class ReloadTLSCertificatesProcedure extends CallableProcedure.BasicProcedure {
    public static final String[] PROCEDURE_NAMESPACE = {"dbms", "security"};
    public static final String PROCEDURE_NAME = "reloadTLS";

    private final DefaultSslPolicyProvider provider;

    public ReloadTLSCertificatesProcedure(DefaultSslPolicyProvider provider) {
        super(procedureSignature(new QualifiedName(PROCEDURE_NAMESPACE, PROCEDURE_NAME))
                .out(VOID)
                .description("Trigger the dynamic reloading of all TLS certificates and configuration.")
                .mode(Mode.DBMS)
                .systemProcedure()
                .admin(true)
                .build());
        this.provider = provider;
    }

    @Override
    public ResourceRawIterator<AnyValue[], ProcedureException> apply(
            Context ctx, AnyValue[] input, ResourceMonitor resourceMonitor) throws ProcedureException {
        provider.reloadPolicies();
        return ResourceRawIterator.empty();
    }
}
