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
package org.neo4j.fabric.stream.summary;

import org.neo4j.graphdb.QueryStatistics;

public class MergedQueryStatistics implements QueryStatistics {
    private int nodesCreated = 0;
    private int nodesDeleted = 0;
    private int relationshipsCreated = 0;
    private int relationshipsDeleted = 0;
    private int propertiesSet = 0;
    private int labelsAdded = 0;
    private int labelsRemoved = 0;
    private int indexesAdded = 0;
    private int indexesRemoved = 0;
    private int constraintsAdded = 0;
    private int constraintsRemoved = 0;
    private int systemUpdates = 0;
    private boolean containsUpdates;
    private boolean containsSystemUpdates;

    public void add(QueryStatistics delta) {
        nodesCreated += delta.getNodesCreated();
        nodesDeleted += delta.getNodesDeleted();
        relationshipsCreated += delta.getRelationshipsCreated();
        relationshipsDeleted += delta.getRelationshipsDeleted();
        propertiesSet += delta.getPropertiesSet();
        labelsAdded += delta.getLabelsAdded();
        labelsRemoved += delta.getLabelsRemoved();
        indexesAdded += delta.getIndexesAdded();
        indexesRemoved += delta.getIndexesRemoved();
        constraintsAdded += delta.getConstraintsAdded();
        constraintsRemoved += delta.getConstraintsRemoved();
        systemUpdates += delta.getSystemUpdates();
        if (delta.containsUpdates()) {
            containsUpdates = true;
        }
        if (delta.containsSystemUpdates()) {
            containsSystemUpdates = true;
        }
    }

    @Override
    public int getNodesCreated() {
        return nodesCreated;
    }

    @Override
    public int getNodesDeleted() {
        return nodesDeleted;
    }

    @Override
    public int getRelationshipsCreated() {
        return relationshipsCreated;
    }

    @Override
    public int getRelationshipsDeleted() {
        return relationshipsDeleted;
    }

    @Override
    public int getPropertiesSet() {
        return propertiesSet;
    }

    @Override
    public int getLabelsAdded() {
        return labelsAdded;
    }

    @Override
    public int getLabelsRemoved() {
        return labelsRemoved;
    }

    @Override
    public int getIndexesAdded() {
        return indexesAdded;
    }

    @Override
    public int getIndexesRemoved() {
        return indexesRemoved;
    }

    @Override
    public int getConstraintsAdded() {
        return constraintsAdded;
    }

    @Override
    public int getConstraintsRemoved() {
        return constraintsRemoved;
    }

    @Override
    public int getSystemUpdates() {
        return systemUpdates;
    }

    @Override
    public boolean containsUpdates() {
        return containsUpdates;
    }

    @Override
    public boolean containsSystemUpdates() {
        return containsSystemUpdates;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        if (containsSystemUpdates) {
            includeIfNonZero(builder, "System updates: ", systemUpdates);
        } else {
            includeIfNonZero(builder, "Nodes created: ", nodesCreated);
            includeIfNonZero(builder, "Relationships created: ", relationshipsCreated);
            includeIfNonZero(builder, "Properties set: ", propertiesSet);
            includeIfNonZero(builder, "Nodes deleted: ", nodesDeleted);
            includeIfNonZero(builder, "Relationships deleted: ", relationshipsDeleted);
            includeIfNonZero(builder, "Labels added: ", labelsAdded);
            includeIfNonZero(builder, "Labels removed: ", labelsRemoved);
            includeIfNonZero(builder, "Indexes added: ", indexesAdded);
            includeIfNonZero(builder, "Indexes removed: ", indexesRemoved);
            includeIfNonZero(builder, "Constraints added: ", constraintsAdded);
            includeIfNonZero(builder, "Constraints removed: ", constraintsRemoved);
        }
        var result = builder.toString();

        if (result.isEmpty()) {
            return "<Nothing happened>";
        } else {
            return result;
        }
    }

    private static void includeIfNonZero(StringBuilder builder, String message, long count) {
        if (count > 0) {
            builder.append(message).append(count).append("\n");
        }
    }
}
