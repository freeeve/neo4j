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
package org.neo4j.genai.util;

import java.io.Serial;
import java.util.OptionalInt;

public class GenAIProcedureException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -2131505931108622859L;

    private final Integer optionalHttpCode;

    public GenAIProcedureException(String message) {
        this(message, (Integer) null);
    }

    public GenAIProcedureException(String message, Integer optionalHttpCode) {
        super(message);
        this.optionalHttpCode = optionalHttpCode;
    }

    public GenAIProcedureException(String message, Throwable cause) {
        super(message, cause);
        this.optionalHttpCode = null;
    }

    /**
     * {@return an optional Http error code associated with this exception}
     */
    public OptionalInt getOptionalHttpCode() {
        return optionalHttpCode == null ? OptionalInt.empty() : OptionalInt.of(optionalHttpCode);
    }
}
