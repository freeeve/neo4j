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

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.ECDSAKeyProvider;
import java.util.Date;
import java.util.UUID;

public class ApiTokenGenerator {
    private final ECDSAKeyProvider keyProvider;

    public ApiTokenGenerator(ECDSAKeyProvider kp) {
        this.keyProvider = kp;
    }

    public String generate(ApiTokenClaims claims) {
        Algorithm algorithm = Algorithm.ECDSA256(this.keyProvider);
        return JWT.create()
                .withKeyId(this.keyProvider.getPrivateKeyId())
                .withIssuer(claims.issuer)
                .withSubject(claims.subject)
                .withClaim("project_id", claims.projectId)
                .withClaim("dbms_id", claims.dbmsId)
                .withAudience(claims.audience)
                .withIssuedAt(new Date())
                .withExpiresAt(claims.expiresAt)
                .withJWTId(UUID.randomUUID().toString())
                .sign(algorithm);
    }

    public static class ApiTokenClaims {
        String issuer;
        String subject;
        String audience;
        Date expiresAt;
        String projectId;
        String dbmsId;

        public ApiTokenClaims() {}

        public ApiTokenClaims(
                String issuer, String subject, String audience, Date expiresAt, String projectId, String dbmsId) {
            this.issuer = issuer;
            this.subject = subject;
            this.audience = audience;
            this.expiresAt = expiresAt;
            this.projectId = projectId;
            this.dbmsId = dbmsId;
        }
    }
}
