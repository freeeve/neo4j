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
package org.neo4j.logging.internal;

import static java.util.Objects.requireNonNull;

import org.neo4j.logging.InternalLog;
import org.neo4j.logging.Neo4jLogMessage;

public class DatabaseLog implements InternalLog {
    private final DatabaseLogIdentifier databaseLogIdentifier;
    private final InternalLog delegate;

    DatabaseLog(DatabaseLogIdentifier databaseLogIdentifier, InternalLog delegate) {
        requireNonNull(delegate, "delegate log cannot be null");
        this.databaseLogIdentifier = databaseLogIdentifier;
        this.delegate = delegate;
    }

    @Override
    public void debug(Neo4jLogMessage message) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public void info(Neo4jLogMessage message) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public void warn(Neo4jLogMessage message) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public void error(Neo4jLogMessage message) {}

    @Override
    public void error(Neo4jLogMessage message, Throwable throwable) {}

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public void debug(String message) {
        delegate.debug(taggedMessage(message));
    }

    @Override
    public void debug(String message, Throwable throwable) {
        delegate.debug(taggedMessage(message, throwable));
    }

    @Override
    public void debug(String format, Object... arguments) {
        delegate.debug(taggedMessage(format, arguments));
    }

    @Override
    public void info(String message) {
        delegate.info(taggedMessage(message));
    }

    @Override
    public void info(String message, Throwable throwable) {
        delegate.info(taggedMessage(message, throwable));
    }

    @Override
    public void info(String format, Object... arguments) {
        delegate.info(taggedMessage(format, arguments));
    }

    @Override
    public void warn(String message) {
        delegate.warn(taggedMessage(message));
    }

    @Override
    public void warn(String message, Throwable throwable) {
        delegate.warn(taggedMessage(message, throwable));
    }

    @Override
    public void warn(String format, Object... arguments) {
        delegate.warn(taggedMessage(format, arguments));
    }

    @Override
    public void error(String message) {
        delegate.error(taggedMessage(message));
    }

    @Override
    public void error(String message, Throwable throwable) {
        delegate.error(taggedMessage(message, throwable));
    }

    @Override
    public void error(String format, Object... arguments) {
        delegate.error(taggedMessage(format, arguments));
    }

    private DatabaseTagLogMessage taggedMessage(String message) {
        return new DatabaseTagLogMessage(databaseLogIdentifier, message, null);
    }

    private DatabaseTagLogMessage taggedMessage(String message, Throwable throwable) {
        return new DatabaseTagLogMessage(databaseLogIdentifier, message, throwable);
    }

    private DatabaseTagLogMessage taggedMessage(String message, Object... arguments) {
        return new DatabaseTagLogMessage(databaseLogIdentifier, String.format(message, arguments), null);
    }
}
