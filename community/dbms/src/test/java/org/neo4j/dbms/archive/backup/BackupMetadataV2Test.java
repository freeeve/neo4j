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
package org.neo4j.dbms.archive.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.neo4j.kernel.database.DatabaseId;
import org.neo4j.storageengine.api.StoreId;

public class BackupMetadataV2Test {

    @Test
    void shouldSerializeDeserializeBackupMetadataV2() throws IOException {
        // given
        var backupDescription = createBackupDescription().withMetadataScript("a".repeat(50000));
        var originalBackupMetadataV2 = BackupMetadataV2.from(backupDescription);

        // when
        var outputStream = new ByteArrayOutputStream();
        originalBackupMetadataV2.writeToStreamV2(outputStream);
        var inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        var recreatedBackupMetadataV2 = BackupMetadataV2.readFromStream(inputStream);

        // then
        assertThat(recreatedBackupMetadataV2).isEqualTo(originalBackupMetadataV2);
    }

    @Test
    void shouldSerializeDeserializeBackupMetadataV2WithPlainTextMetadata() throws IOException {
        // given
        var backupDescription = createBackupDescription();
        var originalBackupMetadataV2 = BackupMetadataV2.from(backupDescription);

        // when
        var outputStream = new ByteArrayOutputStream();
        originalBackupMetadataV2.writeToStreamV1(outputStream);
        var dataStream = new DataOutputStream(outputStream);
        dataStream.writeInt(1);
        dataStream.writeInt("metadataScript".getBytes(StandardCharsets.UTF_8).length);
        dataStream.write("metadataScript".getBytes(StandardCharsets.UTF_8));
        dataStream.writeInt("script".getBytes(StandardCharsets.UTF_8).length);
        dataStream.write("script".getBytes(StandardCharsets.UTF_8));

        var inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        var recreatedBackupMetadataV2 = BackupMetadataV2.readFromStream(inputStream);

        // then
        assertThat(recreatedBackupMetadataV2.toBackupDescription().getMetadataScript())
                .isEqualTo("script");
    }

    @Test
    void shouldCompressMetadata() throws IOException {
        // given
        var backupDescription = createBackupDescription();
        var backupDescriptionWithScript = backupDescription.withMetadataScript("a".repeat(500));
        var originalBackupMetadataV2 = BackupMetadataV2.from(backupDescription);
        var originalBackupMetadataV2WithScript = BackupMetadataV2.from(backupDescriptionWithScript);

        // when
        var outputStream = new ByteArrayOutputStream();
        originalBackupMetadataV2.writeToStreamV2(outputStream);
        var outputStreamWithScript = new ByteArrayOutputStream();
        originalBackupMetadataV2WithScript.writeToStreamV2(outputStreamWithScript);

        // then
        assertThat(outputStreamWithScript.size() - outputStream.size()).isLessThan(500);
    }

    /* This test is used to detect an unexpected change in serialized bytes */
    @Test
    void serializedBytesShouldMatch() throws IOException, NoSuchAlgorithmException {
        // given
        var backupDescription = createBackupDescription().withMetadataScript("script");
        var originalBackupMetadataV2 = BackupMetadataV2.from(backupDescription);

        // when
        var outputStream = new ByteArrayOutputStream();
        originalBackupMetadataV2.writeToStreamV2(outputStream);

        // then
        var messageDigest = MessageDigest.getInstance("MD5");
        var md5 = messageDigest.digest(outputStream.toByteArray());
        var md5String = bytesToHex(md5);
        assertThat(md5String).isEqualTo("0521F3B299CD03C721927F907A45E359");
    }

    private static BackupDescription createBackupDescription() {
        return new BackupDescription(
                        "foo",
                        new StoreId(4, 5, "legacy", "legacy", 1, 1),
                        DatabaseId.SYSTEM_DATABASE_ID,
                        LocalDateTime.of(2024, 5, 30, 15, 54),
                        true,
                        true,
                        true,
                        1,
                        2)
                .withTopology(new BackupDescription.Topology(
                        "name", UUID.fromString("a3b9b4a5-1b78-4093-a1c3-955742878eb4"), 16, OptionalInt.of(8)));
    }

    // from https://stackoverflow.com/questions/9655181/java-convert-a-byte-array-to-a-hex-string
    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = (char) HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = (char) HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
