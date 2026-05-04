/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.parser.v5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

public class Cypher5WatchdogTest {

    @Test
    void cypher5IsFrozen() throws IOException, NoSuchAlgorithmException {
        assertThat(sha256(Paths.get("src/main/antlr4/org/neo4j/cypher/internal/parser/v5/Cypher5Lexer.g4")))
                .as("Cypher 5 is frozen. If you need to make changes, get approval from #team-clg first.")
                .isEqualTo("12121950e43313e678da15de063d54955e13559c0b66c1c24b057549190da1a2");
        assertThat(sha256(Paths.get("src/main/antlr4/org/neo4j/cypher/internal/parser/v5/Cypher5Parser.g4")))
                .as("Cypher 5 is frozen. If you need to make changes, get approval from #team-clg first.")
                .isEqualTo("d4be0e90e2b780615c8c642dbc0ce2cd9adabc3069389038248cff61dbbcd5fd");
    }

    private String sha256(Path filePath) throws IOException, NoSuchAlgorithmException {
        final var digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(filePath)));
    }
}
