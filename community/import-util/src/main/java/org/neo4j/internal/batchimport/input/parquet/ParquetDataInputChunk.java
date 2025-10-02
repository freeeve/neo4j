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
package org.neo4j.internal.batchimport.input.parquet;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.neo4j.batchimport.api.input.IdType;
import org.neo4j.batchimport.api.input.InputEntityVisitor;
import org.neo4j.csv.reader.VectorExtractor;
import org.neo4j.exceptions.TemporalParseException;
import org.neo4j.graphdb.Vector;
import org.neo4j.internal.batchimport.input.Groups;
import org.neo4j.internal.batchimport.input.InputException;
import org.neo4j.values.storable.DateTimeValue;
import org.neo4j.values.storable.DateValue;
import org.neo4j.values.storable.DurationValue;
import org.neo4j.values.storable.LocalDateTimeValue;
import org.neo4j.values.storable.LocalTimeValue;
import org.neo4j.values.storable.PointValue;
import org.neo4j.values.storable.TimeValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.storable.VectorValue;

/**
 * The data chunk to be stuck to a Parquet reader.
 * One chunk equals one file.
 */
class ParquetDataInputChunk implements ParquetInputChunk {
    private ParquetData parquetDataFile;
    private Groups groups;
    private Supplier<ZoneId> defaultTimezoneSupplier;
    private String arrayDelimiter;
    private String vectorDelimiter;
    private IdType idType;
    private Iterator<List<Object>> iterator;
    private Collection<String> filteredLabelsOrTypes;
    private final Map<Object, Collection<String>> labelCache = new HashMap<>();

    @Override
    public boolean readWith(ParquetDataReader reader) {
        try {
            iterator = reader.next();
            if (iterator == null) {
                return false;
            }
            // set up metadata for this reader to avoid repeated parsing those in the reading step
            parquetDataFile = reader.getParquetDataFile();
            groups = reader.getGroups();
            defaultTimezoneSupplier = reader.getDefaultTimezoneSupplier();
            arrayDelimiter = Pattern.quote(reader.getArrayDelimiter());
            vectorDelimiter = Pattern.quote(reader.getVectorDelimiter());
            idType = reader.getIdType();
            filteredLabelsOrTypes = filterEmptyLabelsAndTrim(parquetDataFile.labelsOrType());
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {}

    @Override
    public boolean next(InputEntityVisitor entityToHydrate) throws IOException {
        if (iterator == null || !iterator.hasNext()) {
            return false;
        }

        var columns = parquetDataFile.columns();
        List<Object> readData = iterator.next();
        List<String> labels = new ArrayList<>(filteredLabelsOrTypes);
        StringBuilder idValue = new StringBuilder();
        StringBuilder startIdValue = new StringBuilder();
        StringBuilder endIdValue = new StringBuilder();
        var type = filteredLabelsOrTypes.isEmpty()
                ? ""
                : filteredLabelsOrTypes.iterator().next();
        var isRelationshipEntity = false;
        for (int i = 0; i < readData.size(); i++) {
            var parquetColumn = columns.get(i);
            Object readDatum = readData.get(i);
            if (readDatum == null || isEmptyString(readDatum) || parquetColumn.isIgnoredColumn()) {
                continue;
            }
            // node
            if (parquetColumn.isIdColumn()) {
                if (idType == IdType.STRING
                        && (parquetColumn.columnIdType() == IdType.STRING || parquetColumn.columnIdType() == null)) {
                    if (!idValue.isEmpty()) {
                        idValue.append(ParquetInput.DELIMITER);
                    }
                    idValue.append(resolveIdByType(readDatum, null));
                } else if (idType == IdType.INTEGER || parquetColumn.columnIdType() == IdType.INTEGER) {
                    entityToHydrate.id(
                            resolveIdByType(readDatum, parquetColumn.columnIdType()),
                            groups.get(parquetDataFile.groupName()));
                } else {
                    entityToHydrate.id((Long) resolveIdByType(readDatum, parquetColumn.columnIdType()));
                }
                boolean isActualIdColumn = idType == IdType.ACTUAL && parquetColumn.isIdColumn();
                if (!isActualIdColumn && parquetColumn.hasPropertyName()) {
                    entityToHydrate.property(parquetColumn.propertyName(), convertType(readDatum, parquetColumn), true);
                }
            }
            if (parquetColumn.isLabelColumn()) {
                labels.addAll(readLabelsFromEntry(readDatum));
            }
            // common
            if (parquetColumn.hasPropertyName()
                    && parquetColumn.logicalColumnType() == ParquetLogicalColumnType.PROPERTY) {
                if (readDatum instanceof Map rawDataMap) {
                    Map<String, Object> dataMap = (Map<String, Object>) rawDataMap;
                    for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                        entityToHydrate.property(
                                entry.getKey(),
                                convertType(entry.getValue(), parquetColumn),
                                parquetColumn.isIdentifier());
                    }
                } else {
                    entityToHydrate.property(
                            parquetColumn.propertyName(),
                            convertType(readDatum, parquetColumn),
                            parquetColumn.isIdentifier());
                }
            }
            // relationship
            if (parquetColumn.isStartId()) {
                if (idType == IdType.STRING
                        && (parquetColumn.columnIdType() == IdType.STRING || parquetColumn.columnIdType() == null)) {
                    if (!startIdValue.isEmpty()) {
                        startIdValue.append(ParquetInput.DELIMITER);
                    }
                    startIdValue.append(resolveIdByType(readDatum, null));
                } else {
                    entityToHydrate.startId(
                            resolveIdByType(readDatum, null),
                            groups.get(parquetDataFile.relationshipStartIdGroupName()));
                }
                isRelationshipEntity = true;
            }
            if (parquetColumn.isEndId()) {
                if (idType == IdType.STRING
                        && (parquetColumn.columnIdType() == IdType.STRING || parquetColumn.columnIdType() == null)) {
                    if (!endIdValue.isEmpty()) {
                        endIdValue.append(ParquetInput.DELIMITER);
                    }
                    endIdValue.append(resolveIdByType(readDatum, null));
                } else {
                    entityToHydrate.endId(
                            resolveIdByType(readDatum, null), groups.get(parquetDataFile.relationshipEndIdGroupName()));
                }
                isRelationshipEntity = true;
            }
            if (parquetColumn.isType()) {
                if (readDatum instanceof String typeColumnData && !typeColumnData.isBlank()) {
                    type = typeColumnData;
                }
            }
        }
        if (!isRelationshipEntity && !labels.isEmpty()) {
            entityToHydrate.labels(labels.toArray(new String[] {}));
        }
        if (isRelationshipEntity && type != null && !type.isBlank()) {
            entityToHydrate.type(type);
        }
        if (idType == IdType.STRING && !idValue.isEmpty()) {
            entityToHydrate.id(idValue.toString(), groups.get(parquetDataFile.groupName()));
        }
        if (idType == IdType.STRING && !startIdValue.isEmpty()) {
            entityToHydrate.startId(
                    resolveIdByType(startIdValue.toString(), null),
                    groups.get(parquetDataFile.relationshipStartIdGroupName()));
        }
        if (idType == IdType.STRING && !endIdValue.isEmpty()) {
            entityToHydrate.endId(
                    resolveIdByType(endIdValue.toString(), null),
                    groups.get(parquetDataFile.relationshipStartIdGroupName()));
        }
        entityToHydrate.endOfEntity();
        return true;
    }

    private Object convertType(Object object, ParquetColumn parquetColumn) {
        try {
            if (parquetColumn.isRaw() && parquetColumn.primitiveType().getLogicalTypeAnnotation() == null) {
                return object;
            }

            // for now there is only support for String-based arrays
            if (parquetColumn.isArray() && !(object instanceof List)) {
                String[] parts = object.toString().split(arrayDelimiter);
                Object[] values = new Object[parts.length];
                ParquetColumn nonArrayType = parquetColumn.withoutArray();
                for (int i = 0; i < parts.length; i++) {
                    values[i] = convertType(parts[i], nonArrayType);
                }
                return values;
            } else if (parquetColumn.columnType() == ParquetColumnType.VECTOR) {
                return convertVectorType(object, parquetColumn);
            } else if (object instanceof List) {
                return object;
            }

            return switch (parquetColumn.columnType()) {
                case POINT -> {
                    if (parquetColumn.hasConfiguration()) {
                        yield PointValue.parse(
                                object.toString(), PointValue.parseHeaderInformation(parquetColumn.rawConfiguration()));
                    }
                    yield PointValue.parse(object.toString());
                }
                case DATE ->
                    object instanceof Number number
                            ? DateValue.epochDate(number.intValue())
                            : DateValue.parse(object.toString());
                case TIME ->
                    object instanceof Number number
                            ? TimeValue.time(number.longValue(), ZoneOffset.UTC)
                            : TimeValue.parse(
                                    object.toString(), parquetColumn.getTimezone(defaultTimezoneSupplier), null);
                case DATE_TIME ->
                    DateTimeValue.parse(object.toString(), parquetColumn.getTimezone(defaultTimezoneSupplier), null);
                case LOCAL_TIME -> LocalTimeValue.parse(object.toString());
                case LOCAL_DATE_TIME -> {
                    if (object instanceof Long) {
                        yield LocalDateTimeValue.localDateTime((Long) object / 1000000L, 0);
                    } else {
                        try {
                            yield LocalDateTimeValue.parse(object.toString());
                        } catch (TemporalParseException e) {
                            // this could happen if the column type is adjusted to UTC (with zone) but the column header
                            // defines this just as a localdatetime
                            yield LocalDateTimeValue.localDateTime(
                                    DateTimeValue.parse(object.toString(), () -> ZoneId.of(ZoneOffset.UTC.getId()))
                                            .asObjectCopy()
                                            .toLocalDateTime());
                        }
                    }
                }
                case DURATION -> DurationValue.parse(object.toString());
                case INT -> Integer.valueOf(object.toString());
                case SHORT -> Short.valueOf(object.toString());
                case STRING -> object.toString();
                case LONG -> Long.valueOf(object.toString());
                case BYTE -> Byte.parseByte(object.toString());
                case DOUBLE -> Double.parseDouble(object.toString());
                case FLOAT -> Float.parseFloat(object.toString());
                default -> object;
            };
        } catch (RuntimeException e) {
            throw new InputException(
                    "could not convert %s to %s: %s"
                            .formatted(object.toString(), parquetColumn.columnType(), e.getMessage()),
                    e);
        }
    }

    private VectorValue convertVectorType(Object object, ParquetColumn parquetColumn) {
        final String[] parts = object.toString().split(vectorDelimiter);

        final var headerInformation = VectorExtractor.parseHeaderInformation(parquetColumn.configuration());
        final var dimensions = headerInformation.getDimensions();
        final var coordinateType = headerInformation.getCoordinateType();

        if (dimensions != parts.length) {
            throw new IllegalArgumentException("Header specified %d dimensions, but vector has %d dimensions: %s"
                    .formatted(dimensions, parts.length, object));
        }

        return switch (coordinateType) {
            case Vector.CoordinateType.INTEGER8 -> {
                final var innerColumn = parquetColumn.withColumnType(ParquetColumnType.resolve("byte"));
                final byte[] values = new byte[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    values[i] = (byte) convertType(parts[i], innerColumn);
                }
                yield Values.int8Vector(values);
            }
            case Vector.CoordinateType.INTEGER16 -> {
                final var innerColumn = parquetColumn.withColumnType(ParquetColumnType.resolve("short"));
                short[] values = new short[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    values[i] = (short) convertType(parts[i], innerColumn);
                }
                yield Values.int16Vector(values);
            }
            case Vector.CoordinateType.INTEGER32 -> {
                final var innerColumn = parquetColumn.withColumnType(ParquetColumnType.resolve("int"));
                int[] values = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    values[i] = (int) convertType(parts[i], innerColumn);
                }
                yield Values.int32Vector(values);
            }
            case Vector.CoordinateType.INTEGER64 -> {
                final var innerColumn = parquetColumn.withColumnType(ParquetColumnType.resolve("long"));
                long[] values = new long[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    values[i] = (long) convertType(parts[i], innerColumn);
                }
                yield Values.int64Vector(values);
            }
            case Vector.CoordinateType.FLOAT32 -> {
                final var innerColumn = parquetColumn.withColumnType(ParquetColumnType.resolve("float"));
                float[] values = new float[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    values[i] = (float) convertType(parts[i], innerColumn);
                }
                yield Values.float32Vector(values);
            }
            case Vector.CoordinateType.FLOAT64 -> {
                final var innerColumn = parquetColumn.withColumnType(ParquetColumnType.resolve("double"));
                double[] values = new double[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    values[i] = (double) convertType(parts[i], innerColumn);
                }
                yield Values.float64Vector(values);
            }
        };
    }

    private boolean isEmptyString(Object object) {
        return object instanceof String stringValue && stringValue.isEmpty();
    }

    private Object resolveIdByType(Object id, IdType columnIdType) {
        if (id instanceof String stringId) {
            return stringId;
        } else if (id instanceof Long longId) {
            return longId;
        } else if (id instanceof Integer intId) {
            if (columnIdType == IdType.INTEGER) {
                return intId;
            }
            return intId.longValue();
        }

        throw new IllegalArgumentException("Cannot convert id of type " + id.getClass());
    }

    private Collection<String> filterEmptyLabelsAndTrim(Collection<String> labels) {
        return labels.stream().filter(s -> !s.isEmpty()).map(String::trim).collect(Collectors.toSet());
    }

    private Collection<String> readLabelsFromEntry(Object readDatum) {
        return labelCache.computeIfAbsent(
                readDatum,
                (read) -> filterEmptyLabelsAndTrim(Arrays.asList(read.toString().split(arrayDelimiter))));
    }
}
