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
package org.neo4j.fleetmanagement.communication.upstream;

import com.auth0.jwt.interfaces.ECDSAKeyProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

public class ApiKeyProvider implements ECDSAKeyProvider {
    private final ApiKey key;

    public ApiKeyProvider(ApiKey key) {
        this.key = key;
    }

    @Override
    public ECPrivateKey getPrivateKey() {
        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        PrivateKey key;
        try {
            key = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(this.key.privateKey)));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }

        return (ECPrivateKey) key;
    }

    @Override
    public String getPrivateKeyId() {
        return this.key.keyId;
    }

    @Override
    public ECPublicKey getPublicKeyById(String arg0) {
        return null;
    }

    public static class ApiKey {
        @JsonProperty("kid")
        String keyId;

        @JsonProperty("private_key")
        String privateKey;

        @JsonProperty("project_id")
        String projectId;

        @JsonProperty("expiry_time")
        String expiryTime;

        public ApiKey() {}

        public String keyId() {
            return keyId;
        }

        public String privateKey() {
            return privateKey;
        }

        public String projectId() {
            return projectId;
        }

        public Instant expiryTime() {
            return Instant.parse(expiryTime);
        }
    }
}
