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
package org.neo4j.fleetmanagement.communication;

import java.util.concurrent.atomic.AtomicReference;
import org.neo4j.fleetmanagement.communication.upstream.Upstream;
import org.neo4j.fleetmanagement.configuration.Configuration;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.transactions.ITransactor;

public abstract class BufferedReportingService<T> extends AbstractReportingService {
    // Maximum number of missed reports before pausing log collection
    private static final int MAX_BUFFERED_REPORTS = 3;

    private final AtomicReference<T> current;
    private int missedReports = 0;

    protected BufferedReportingService(
            ITransactor transactor, Upstream upstream, State state, Configuration configuration, T initialValue) {
        super(transactor, upstream, state, configuration);
        this.current = new AtomicReference<>(initialValue);
    }

    public BufferedReportingService(
            ITransactor transactor, Upstream upstream, State state, Configuration configuration) {
        super(transactor, upstream, state, configuration);
        this.current = new AtomicReference<>();
    }

    protected T getCurrent() {
        return current.get();
    }

    protected T getPrevious(T nextValue) {
        return current.getAndSet(nextValue);
    }

    protected boolean isBufferLimitReached() {
        return missedReports >= MAX_BUFFERED_REPORTS;
    }

    protected void updateBufferSize() {
        this.missedReports++;
    }

    protected void resetBuffer() {
        this.missedReports = 0;
    }
}
