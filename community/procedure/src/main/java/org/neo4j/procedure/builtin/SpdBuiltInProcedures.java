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
package org.neo4j.procedure.builtin;

import java.util.stream.Stream;
import org.neo4j.kernel.api.KernelTransaction;

/**
 * An optimised version of build-in procedures (the ones in {@link org.neo4j.procedure.builtin.BuiltInProcedures})
 * for sharded property databases. The code works only on SPD main graph, so it is necessary to check
 * {@link #isGraphShard()} before invoking any of the procedures.
 */
public interface SpdBuiltInProcedures {

    SpdBuiltInProcedures COMMUNITY_EDITION_IMPL = new SpdBuiltInProcedures() {

        @Override
        public boolean isSpd() {
            return false;
        }

        @Override
        public boolean isGraphShard() {
            return false;
        }

        @Override
        public String virtualSpdName() {
            throw unsupported();
        }

        @Override
        public Stream<NodePropertySchemaInfoResult> nodePropertySchema(KernelTransaction kernelTransaction) {
            throw unsupported();
        }

        @Override
        public Stream<RelationshipPropertySchemaInfoResult> relationshipPropertySchema(
                KernelTransaction kernelTransaction) {
            throw unsupported();
        }

        @Override
        public void prepareForReplanning() {
            throw unsupported();
        }

        @Override
        public void resampleOutdatedIndexes() {
            throw unsupported();
        }

        @Override
        public void resampleIndex(String indexName) {
            throw unsupported();
        }

        @Override
        public void clearQueryCaches() {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Trying to use SPD procedure outside of SPD context");
        }
    };

    boolean isSpd();

    boolean isGraphShard();

    String virtualSpdName();

    Stream<NodePropertySchemaInfoResult> nodePropertySchema(KernelTransaction kernelTransaction);

    Stream<RelationshipPropertySchemaInfoResult> relationshipPropertySchema(KernelTransaction kernelTransaction);

    void prepareForReplanning();

    void resampleOutdatedIndexes();

    void resampleIndex(String indexName);

    void clearQueryCaches();
}
