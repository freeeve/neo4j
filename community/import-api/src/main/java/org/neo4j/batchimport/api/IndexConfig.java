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

import java.util.function.Predicate;
import org.neo4j.function.Predicates;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.util.Preconditions;

public class IndexConfig {

    private boolean createLabelIndex;
    private boolean createRelationTypeIndex;
    private Predicate<IndexDescriptor> excludeFromPopulating = Predicates.alwaysFalse();

    public IndexConfig withLabelIndex() {
        this.createLabelIndex = true;
        return this;
    }

    public IndexConfig withRelationshipTypeIndex() {
        this.createRelationTypeIndex = true;
        return this;
    }

    public IndexConfig excludeFromPopulating(Predicate<IndexDescriptor> excludeFromPopulating) {
        this.excludeFromPopulating = Preconditions.requireNonNull(
                excludeFromPopulating, "Exclude from populating predicate must not be null");
        return this;
    }

    public boolean createLabelIndex() {
        return createLabelIndex;
    }

    public boolean createRelationshipIndex() {
        return createRelationTypeIndex;
    }

    public boolean isExcludedFromPopulating(IndexDescriptor descriptor) {
        return excludeFromPopulating.test(descriptor);
    }

    public static IndexConfig create() {
        return new IndexConfig();
    }
}
