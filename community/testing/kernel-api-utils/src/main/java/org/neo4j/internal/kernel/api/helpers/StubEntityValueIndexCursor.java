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
package org.neo4j.internal.kernel.api.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.neo4j.internal.kernel.api.DefaultCloseListenable;
import org.neo4j.internal.kernel.api.EntityIndexCursor;
import org.neo4j.internal.kernel.api.KernelReadTracer;
import org.neo4j.internal.kernel.api.ValueIndexCursor;
import org.neo4j.values.storable.Value;

public class StubEntityValueIndexCursor extends DefaultCloseListenable implements EntityIndexCursor, ValueIndexCursor {
    private int position = -1;
    protected final List<ValueIndexResult> results = new ArrayList<>();
    private boolean closed;

    record ValueIndexResult(long entityId, float score, Value... values) {}

    protected StubEntityValueIndexCursor() {}

    public StubEntityValueIndexCursor withEntity(long id, float score, Value... vs) {
        results.add(new ValueIndexResult(id, score, vs));
        return this;
    }

    public StubEntityValueIndexCursor withEntity(long id, Value... vs) {
        return withEntity(id, Float.NaN, vs);
    }

    @Override
    public long reference() {
        return accessResult((result) -> result.entityId, () -> -1L);
    }

    @Override
    public float score() {
        return accessResult((result) -> result.score, () -> Float.NaN);
    }

    @Override
    public boolean next() {
        checkOpen();
        return ++position < results.size();
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Cursor is closed");
        }
    }

    @Override
    public void closeInternal() {
        this.closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void setTracer(KernelReadTracer tracer) {}

    @Override
    public void removeTracer() {}

    @Override
    public int numberOfProperties() {
        return accessResult((result) -> result.values().length, () -> 0);
    }

    @Override
    public boolean hasValue() {
        return numberOfProperties() > 0;
    }

    @Override
    public Value propertyValue(int offset) {
        return accessResult((value) -> value.values()[offset], () -> {
            throw new NullPointerException();
        });
    }

    protected <T> T accessResult(Function<ValueIndexResult, T> existingResult, Supplier<T> defaultSupplier) {
        return position >= 0 && position < results.size()
                ? existingResult.apply(results.get(position))
                : defaultSupplier.get();
    }
}
