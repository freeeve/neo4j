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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Base64;
import org.neo4j.fleetmanagement.communication.upstream.ApiKeyProvider;

public class TokenUtils {
    public static ApiKeyProvider.ApiKey parseToken(String token) throws IOException {
        Base64.Decoder decoder = Base64.getDecoder();
        var keyMapBytes = decoder.decode(token);
        var om = new ObjectMapper();
        return om.readValue(keyMapBytes, ApiKeyProvider.ApiKey.class);
    }
}
