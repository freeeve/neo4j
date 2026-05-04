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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.compress.compressors.CompressorStreamFactory.GZIP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

public class BackupMetadataV2 extends BackupMetadataV1 {
    public static final int VERSION = 2;

    private static final String METADATA_SCRIPT_FIELD = "metadataScript";
    private static final String COMPRESSED_METADATA_SCRIPT_FIELD = "compressedMetadataScript";

    private static final String TOPOLOGY_VERSION = "1";
    private static final String TOPOLOGY_VERSION_FIELD = "topology_version";
    private static final String VIRTUAL_NAME_FIELD = "virtual_name";
    private static final String VIRTUAL_ID_FIELD = "virtual_id";
    private static final String INDEX_FIELD = "shard_index";
    private static final String SHARD_COUNT_FIELD = "shard_count";

    private static final Set<String> KEYS_OF_COMPRESSED_VALUES = Set.of(COMPRESSED_METADATA_SCRIPT_FIELD);

    private final BackupMetadataV1 backupMetadataV1;
    private final Map<String, String> additionalFields;

    public static BackupMetadataV2 readFromStream(InputStream inputStream) throws IOException {
        return readMetadataV2(inputStream);
    }

    public static BackupMetadataV2 from(BackupDescription description) {
        var backupMetadataV1 = new BackupMetadataV1(description);
        var metadataScript = description.getMetadataScript();
        var additionalFields = new HashMap<String, String>();
        if (metadataScript != null) {
            additionalFields.put(COMPRESSED_METADATA_SCRIPT_FIELD, metadataScript);
        }
        description.getTopology().ifPresent(t -> writeV1Topology(t, additionalFields));

        return new BackupMetadataV2(backupMetadataV1, additionalFields);
    }

    static BackupMetadataV2 readMetadataV2(InputStream inputStream) throws IOException {
        var backupMetadataV1 = readMetadataV1(inputStream);
        var additionalFields = readMap(inputStream);
        return new BackupMetadataV2(backupMetadataV1, additionalFields);
    }

    private BackupMetadataV2(BackupMetadataV1 backupMetadataV1, Map<String, String> additionalFields) {
        super(
                backupMetadataV1.getDatabaseName(),
                backupMetadataV1.getStoreId(),
                backupMetadataV1.getDatabaseId(),
                backupMetadataV1.getBackupTime(),
                backupMetadataV1.getLowestAppendIndex(),
                backupMetadataV1.getHighestAppendIndex(),
                backupMetadataV1.isRecovered(),
                backupMetadataV1.isCompressed(),
                backupMetadataV1.isFull());
        this.backupMetadataV1 = backupMetadataV1;
        this.additionalFields = additionalFields;
    }

    public void writeToStreamV2(OutputStream compressionStream) throws IOException {
        writeToStreamV1(compressionStream);
        writeMap(compressionStream, additionalFields);
    }

    private void writeMap(OutputStream compressionStream, Map<String, String> additionalFields) throws IOException {
        var dataStream = new DataOutputStream(compressionStream);
        int mapSize = additionalFields.size();
        dataStream.writeInt(mapSize);
        for (var field : additionalFields.entrySet()) {
            writeString(dataStream, field.getKey());
            if (KEYS_OF_COMPRESSED_VALUES.contains(field.getKey())) {
                writeCompressedString(dataStream, field.getValue());
            } else {
                writeString(dataStream, field.getValue());
            }
        }
        dataStream.flush();
    }

    private static Map<String, String> readMap(InputStream inputStream) throws IOException {
        var deserializedMap = new HashMap<String, String>();
        var dataStream = new DataInputStream(inputStream);
        var mapSize = dataStream.readInt();
        for (int i = 0; i < mapSize; i++) {
            var key = readString(dataStream);
            var value =
                    KEYS_OF_COMPRESSED_VALUES.contains(key) ? readCompressedString(dataStream) : readString(dataStream);
            deserializedMap.put(key, value);
        }
        return deserializedMap;
    }

    private static void writeString(DataOutputStream outputStream, String value) throws IOException {
        byte[] data = value.getBytes(UTF_8);
        outputStream.writeInt(data.length);
        outputStream.write(data);
    }

    private static String readString(DataInputStream inputStream) throws IOException {
        int length = inputStream.readInt();
        return new String(inputStream.readNBytes(length), UTF_8);
    }

    @Override
    public BackupDescription toBackupDescription() {
        var metadataScript = Optional.ofNullable(additionalFields.get(COMPRESSED_METADATA_SCRIPT_FIELD))
                .orElseGet(() -> additionalFields.get(METADATA_SCRIPT_FIELD));
        var topology = Optional.ofNullable(additionalFields.get(TOPOLOGY_VERSION_FIELD))
                .filter(TOPOLOGY_VERSION::equals)
                .map(ignore -> readV1Topology())
                .orElse(null);
        return super.toBackupDescription().withMetadataScript(metadataScript).withTopology(topology);
    }

    private BackupDescription.Topology readV1Topology() {
        var virtualName = additionalFields.get(VIRTUAL_NAME_FIELD);
        var virtualId = UUID.fromString(additionalFields.get(VIRTUAL_ID_FIELD));
        var shardCount = Integer.parseInt(additionalFields.get(SHARD_COUNT_FIELD));
        var shardIndexStr = additionalFields.get(INDEX_FIELD);
        var shardIndex = shardIndexStr == null ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(shardIndexStr));
        return new BackupDescription.Topology(virtualName, virtualId, shardCount, shardIndex);
    }

    private static void writeV1Topology(BackupDescription.Topology topology, HashMap<String, String> additionalFields) {
        additionalFields.put(TOPOLOGY_VERSION_FIELD, TOPOLOGY_VERSION);
        additionalFields.put(VIRTUAL_NAME_FIELD, topology.virtualName());
        additionalFields.put(VIRTUAL_ID_FIELD, topology.virtualId().toString());
        additionalFields.put(SHARD_COUNT_FIELD, String.valueOf(topology.shardCount()));
        topology.index().ifPresent(index -> additionalFields.put(INDEX_FIELD, String.valueOf(index)));
    }

    private static void writeCompressedString(DataOutputStream outputStream, String value) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var cos = new CompressorStreamFactory().createCompressorOutputStream(GZIP, output)) {
            cos.write(value.getBytes(UTF_8));
        }
        outputStream.writeInt(output.size());
        outputStream.write(output.toByteArray());
    }

    private static String readCompressedString(DataInputStream inputStream) throws IOException {
        int length = inputStream.readInt();
        var input = new ByteArrayInputStream(inputStream.readNBytes(length));
        try (var cis = new CompressorStreamFactory().createCompressorInputStream(GZIP, input)) {
            return new String(cis.readAllBytes(), UTF_8);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BackupMetadataV2 that = (BackupMetadataV2) o;
        return Objects.equals(backupMetadataV1, that.backupMetadataV1)
                && Objects.equals(additionalFields, that.additionalFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backupMetadataV1, additionalFields);
    }

    @Override
    public String toString() {
        return "BackupMetadataV2{" + "backupMetadataV1="
                + backupMetadataV1 + ", additionalFields="
                + additionalFields + '}';
    }
}
