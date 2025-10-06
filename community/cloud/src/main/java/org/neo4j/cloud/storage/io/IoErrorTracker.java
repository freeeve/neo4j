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
package org.neo4j.cloud.storage.io;

import java.io.IOException;

public interface IoErrorTracker {
    /**
     * @throws IOException checks whether any {@link IOException}s have occurred and throws one if true.
     */
    void ensureNoErrors() throws IOException;

    /**
     *
     * @param ex the new {@link IOException}
     * @return an {@link IOException} that tracks the provided and any previous {@link IOException}s that occurred.
     */
    IOException updateErrors(IOException ex);
}
