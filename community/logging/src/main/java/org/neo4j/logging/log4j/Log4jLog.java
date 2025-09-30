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
package org.neo4j.logging.log4j;

import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.Neo4jLogMessage;

/**
 * A {@link InternalLog} implementation that uses the Log4j configuration the logger is connected to.
 */
public class Log4jLog extends ExtendedLoggerWrapper implements InternalLog {
    // Setting controlling specific enrichment for errors (internal use only)
    private final boolean internalErrorMarkersEnabled;

    /**
     * Package-private specifically to not leak Logger outside logging module. Should not be used outside of the logging module - {@link
     * Log4jLogProvider#getLog} should be used instead.
     */
    Log4jLog(ExtendedLogger logger, boolean internalErrorMarkersEnabled) {
        super(logger, logger.getName(), logger.getMessageFactory());
        this.internalErrorMarkersEnabled = internalErrorMarkersEnabled;
    }

    Log4jLog(ExtendedLogger logger) {
        this(logger, false);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void debug(Neo4jLogMessage message) {
        if (internalErrorMarkersEnabled) {
            Neo4jLogMarker marker = message.getMarker();
            logger.debug(marker != null ? marker.log4jMarker : null, message);
        } else {
            logger.debug(message);
        }
    }

    @Override
    public void info(Neo4jLogMessage message) {
        if (internalErrorMarkersEnabled) {
            Neo4jLogMarker marker = message.getMarker();
            logger.info(marker != null ? marker.log4jMarker : null, message);
        } else {
            logger.info(message);
        }
    }

    @Override
    public void warn(Neo4jLogMessage message) {
        if (internalErrorMarkersEnabled) {
            Neo4jLogMarker marker = message.getMarker();
            logger.warn(marker != null ? marker.log4jMarker : null, message);
        } else {
            logger.warn(message);
        }
    }

    @Override
    public void error(Neo4jLogMessage message) {
        if (internalErrorMarkersEnabled) {
            Neo4jLogMarker marker = message.getMarker();
            logger.error(marker != null ? marker.log4jMarker : null, message);
        } else {
            logger.error(message);
        }
    }

    @Override
    public void error(Neo4jLogMessage message, Throwable throwable) {
        if (internalErrorMarkersEnabled) {
            Neo4jLogMarker marker = message.getMarker();
            logger.error(marker != null ? marker.log4jMarker : null, message, throwable);
        } else {
            logger.error(message, throwable);
        }
    }
}
