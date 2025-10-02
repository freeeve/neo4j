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
package org.neo4j.kernel.impl.core;

import static org.neo4j.internal.kernel.api.security.LoginContext.AUTH_DISABLED;

import java.util.function.Supplier;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.Vector;
import org.neo4j.kernel.api.Kernel;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.KernelTransaction.Type;
import org.neo4j.storageengine.VectorStoreCreator;

/// Vector store are created dynamically. We discover the need to create a missing vector store
/// when generating commands for writing a vector property to a store that does not yet exist.
/// At this point, we cannot defer the creation of the vector store, since we need an ID generator
/// to be able to generate the command.
///
/// Therefore, we create the vector store in an isolated transaction, while the original transaction
/// waits until the vector-store-create-transaction has successfully committed.
///
/// That way, all writes to VectorStores GBP tree are transactional.
public final class IsolatedTransactionVectorStoreCreator implements VectorStoreCreator {
    private final Supplier<Kernel> kernelSupplier;

    public IsolatedTransactionVectorStoreCreator(Supplier<Kernel> kernelSupplier) {
        this.kernelSupplier = kernelSupplier;
    }

    @Override
    public synchronized void createVectorStore(Vector.CoordinateType coordinateType, int dimensions)
            throws KernelException {
        final Kernel kernel = kernelSupplier.get();
        try (final KernelTransaction tx = kernel.beginTransaction(Type.IMPLICIT, AUTH_DISABLED)) {
            tx.dataWrite().createVectorStore(coordinateType, dimensions);
            tx.commit();
        }
    }
}
