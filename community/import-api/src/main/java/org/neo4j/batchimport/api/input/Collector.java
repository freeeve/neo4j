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
package org.neo4j.batchimport.api.input;

import static java.lang.String.format;

import java.util.Map;
import org.neo4j.common.EntityType;

/**
 * Collects items and is {@link #close() closed} after any and all items have been collected.
 * The {@link Collector} is responsible for closing whatever closeable resource received from the importer.
 */
public interface Collector extends AutoCloseable {
    void collectBadRelationship(
            Object startId,
            Group startIdGroup,
            Object type,
            Object endId,
            Group endIdGroup,
            Object specificValue,
            String source,
            long lineNumber);

    void collectDuplicateNode(Object id, long actualId, Group group, String source, long lineNumber);

    void collectEntityViolatingConstraint(
            Object id,
            long actualId,
            Map<String, Object> properties,
            String constraintDescription,
            EntityType entityType,
            String source,
            long lineNumber);

    void collectRelationshipViolatingConstraint(
            Map<String, Object> properties,
            String constraintDescription,
            Object startId,
            Group startIdGroup,
            String type,
            Object endId,
            Group endIdGroup,
            String source,
            long lineNumber);

    void collectExtraColumns(String source, long row, String value);

    /**
     * @param entityType the type of entity that relates to the {@link org.neo4j.internal.schema.SchemaCommand} being
     *                  applied (or <code>null</code> if the type is unknown)
     * @param failureMessage the failure message that resulted when applying a {@link org.neo4j.internal.schema.SchemaCommand}
     */
    void collectSchemaCommandFailure(EntityType entityType, String failureMessage);

    void collectOtherNodeViolation(String format, Object... parameters);

    void collectOtherRelationshipViolation(String format, Object... parameters);

    long badEntries();

    boolean isCollectingBadRelationships();

    /**
     * Flushes whatever changes to the underlying resource supplied from the importer.
     */
    @Override
    void close();

    Collector EMPTY = new Collector() {
        @Override
        public void collectExtraColumns(String source, long row, String value) {}

        @Override
        public void close() {}

        @Override
        public long badEntries() {
            return 0;
        }

        @Override
        public void collectBadRelationship(
                Object startId,
                Group startIdGroup,
                Object type,
                Object endId,
                Group endIdGroup,
                Object specificValue,
                String source,
                long lineNumber) {}

        @Override
        public void collectDuplicateNode(Object id, long actualId, Group group, String source, long lineNumber) {}

        @Override
        public void collectEntityViolatingConstraint(
                Object id,
                long actualId,
                Map<String, Object> properties,
                String constraintDescription,
                EntityType entityType,
                String source,
                long lineNumber) {}

        @Override
        public void collectRelationshipViolatingConstraint(
                Map<String, Object> properties,
                String constraintDescription,
                Object startId,
                Group startIdGroup,
                String type,
                Object endId,
                Group endIdGroup,
                String source,
                long lineNumber) {}

        @Override
        public void collectSchemaCommandFailure(EntityType entityType, String failureMessage) {}

        @Override
        public void collectOtherNodeViolation(String format, Object... parameters) {}

        @Override
        public void collectOtherRelationshipViolation(String format, Object... parameters) {}

        @Override
        public boolean isCollectingBadRelationships() {
            return true;
        }
    };

    Collector STRICT = new Collector() {
        @Override
        public void collectExtraColumns(String source, long row, String value) {
            throw new IllegalStateException(format("Bad extra column '%s' index:%d in '%s'", value, row, source));
        }

        @Override
        public void close() {}

        @Override
        public long badEntries() {
            return 0;
        }

        @Override
        public void collectBadRelationship(
                Object startId,
                Group startIdGroup,
                Object type,
                Object endId,
                Group endIdGroup,
                Object specificValue,
                String source,
                long lineNumber) {
            throw new IllegalStateException(format(
                    "Bad relationship (%s:%s)-[%s]->(%s:%s) %s, index:%d in '%s'",
                    startId, startIdGroup, type, endId, endIdGroup, specificValue, lineNumber, source));
        }

        @Override
        public void collectDuplicateNode(Object id, long actualId, Group group, String source, long lineNumber) {
            throw new IllegalStateException(format(
                    "Bad duplicate node %s:%s id:%d, index:%d in '%s'", id, group, actualId, lineNumber, source));
        }

        @Override
        public void collectEntityViolatingConstraint(
                Object id,
                long actualId,
                Map<String, Object> properties,
                String constraintDescription,
                EntityType entityType,
                String source,
                long lineNumber) {
            throw new IllegalStateException(format(
                    "Bad %s with properties %s violating constraint %s id:%s, index:%d in '%s'",
                    entityType == EntityType.NODE ? "node" : "relationship",
                    properties,
                    constraintDescription,
                    id,
                    lineNumber,
                    source));
        }

        @Override
        public void collectRelationshipViolatingConstraint(
                Map<String, Object> properties,
                String constraintDescription,
                Object startId,
                Group startIdGroup,
                String type,
                Object endId,
                Group endIdGroup,
                String source,
                long lineNumber) {
            throw new IllegalStateException(format(
                    "Bad relationship (%s:%s)-[%s]->(%s:%s) with properties %s violating constraint %s, index:%d in '%s'",
                    startId,
                    startIdGroup,
                    type,
                    endId,
                    endIdGroup,
                    properties,
                    constraintDescription,
                    lineNumber,
                    source));
        }

        @Override
        public void collectSchemaCommandFailure(EntityType entityType, String failureMessage) {
            throw new IllegalStateException(failureMessage);
        }

        @Override
        public void collectOtherNodeViolation(String format, Object... parameters) {
            throw new IllegalStateException(format(format, parameters));
        }

        @Override
        public void collectOtherRelationshipViolation(String format, Object... parameters) {
            throw new IllegalStateException(format(format, parameters));
        }

        @Override
        public boolean isCollectingBadRelationships() {
            return false;
        }
    };

    class Adapter implements Collector {
        @Override
        public void collectBadRelationship(
                Object startId,
                Group startIdGroup,
                Object type,
                Object endId,
                Group endIdGroup,
                Object specificValue,
                String source,
                long lineNumber) {}

        @Override
        public void collectDuplicateNode(Object id, long actualId, Group group, String source, long lineNumber) {}

        @Override
        public void collectEntityViolatingConstraint(
                Object id,
                long actualId,
                Map<String, Object> properties,
                String constraintDescription,
                EntityType entityType,
                String source,
                long lineNumber) {}

        @Override
        public void collectRelationshipViolatingConstraint(
                Map<String, Object> properties,
                String constraintDescription,
                Object startId,
                Group startIdGroup,
                String type,
                Object endId,
                Group endIdGroup,
                String source,
                long lineNumber) {}

        @Override
        public void collectExtraColumns(String source, long row, String value) {}

        @Override
        public void collectSchemaCommandFailure(EntityType entityType, String failureMessage) {}

        @Override
        public void collectOtherNodeViolation(String format, Object... parameters) {}

        @Override
        public void collectOtherRelationshipViolation(String format, Object... parameters) {}

        @Override
        public long badEntries() {
            return 0;
        }

        @Override
        public boolean isCollectingBadRelationships() {
            return false;
        }

        @Override
        public void close() {}
    }
}
