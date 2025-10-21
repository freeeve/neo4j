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
package org.neo4j.fleetmanagement.utils;

import org.neo4j.logging.Log;

public class Logger {

    private final Log log;
    private static Logger instance;
    private static boolean payloadLoggingEnabled = false;
    private static boolean debugEnabled = false;

    private Logger(Log log) {
        this.log = log;
    }

    public static synchronized void initLogger(Log log) {
        if (instance == null) {
            instance = new Logger(log);
        }
    }

    public static synchronized Log getNeo4jLogger() {
        if (instance == null) {
            throw new IllegalStateException("Logger has not been initialized. Call initLogger first.");
        }
        return instance.log;
    }

    public static synchronized Logger getFleetManagerLogger() {
        if (instance == null) {
            throw new IllegalStateException("Logger has not been initialized. Call initLogger first.");
        }
        return instance;
    }

    public static void setDebugEnabled(Boolean enabled) {
        debugEnabled = Boolean.TRUE.equals(enabled);
    }

    public static void setPayloadLoggingEnabled(Boolean enabled) {
        payloadLoggingEnabled = Boolean.TRUE.equals(enabled);
    }

    public void debug(String message) {
        if (debugEnabled) {
            log.info(message);
        }
    }

    public void payload(String message) {
        if (payloadLoggingEnabled) {
            log.info(message);
        }
    }
}
