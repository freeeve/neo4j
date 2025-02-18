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
package org.neo4j.batchimport.api;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.neo4j.internal.schema.IndexType;

public class IndexConfig {

    private final MutableSet<IndexType> excludedIndexTypes = Sets.mutable.empty();

    private boolean createLabelIndex;
    private boolean createRelationTypeIndex;

    public IndexConfig withLabelIndex() {
        this.createLabelIndex = true;
        return this;
    }

    public IndexConfig withRelationshipTypeIndex() {
        this.createRelationTypeIndex = true;
        return this;
    }

    public IndexConfig excludeTypeFromPopulating(IndexType indexType) {
        excludedIndexTypes.add(indexType);
        return this;
    }

    public boolean createLabelIndex() {
        return createLabelIndex;
    }

    public boolean createRelationshipIndex() {
        return createRelationTypeIndex;
    }

    public boolean isTypeExcludedFromPopulating(IndexType type) {
        return excludedIndexTypes.contains(type);
    }

    public static IndexConfig create() {
        return new IndexConfig();
    }
}
