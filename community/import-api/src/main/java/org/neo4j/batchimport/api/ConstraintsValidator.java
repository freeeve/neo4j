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

import java.io.IOException;
import java.util.Set;
import org.neo4j.internal.helpers.progress.ProgressListener;
import org.neo4j.internal.schema.ConstraintDescriptor;

/**
 * Used by the {@link BatchImporter} to validate constraints during the import process.
 */
public interface ConstraintsValidator {

    /**
     * @param constraint the constraint descriptor to validate
     * @return <code>true</code> if the constraint added will be validated
     */
    boolean add(ConstraintDescriptor constraint);

    /**
     * Validates all the previously {@link ConstraintsValidator#add(ConstraintDescriptor)} constraints
     * @param progressListener to track the progress of the validation
     * @return the constraint descriptors that failed validation
     * @throws IOException if unable to access the data to validate
     */
    Set<ConstraintDescriptor> validate(ProgressListener progressListener) throws IOException;
}
