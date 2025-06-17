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
package org.neo4j.internal.indexcommand;

import org.neo4j.storageengine.api.UpdateMode;

public enum IndexCommandSelector {
    NORMAL {
        @Override
        public <T> T getBefore(IndexUpdateCommand<T> command) {
            return command.getBefore();
        }

        @Override
        public <T> T getAfter(IndexUpdateCommand<T> command) {
            return command.getAfter();
        }

        @Override
        public <T> UpdateMode mode(IndexUpdateCommand<T> command) {
            return command.getUpdateMode();
        }
    },
    REVERSE {
        @Override
        public <T> T getBefore(IndexUpdateCommand<T> command) {
            return command.getAfter();
        }

        @Override
        public <T> T getAfter(IndexUpdateCommand<T> command) {
            return command.getBefore();
        }

        @Override
        public <T> UpdateMode mode(IndexUpdateCommand<T> command) {
            return switch (command.getUpdateMode()) {
                case ADDED -> UpdateMode.REMOVED;
                case REMOVED -> UpdateMode.ADDED;
                case CHANGED -> UpdateMode.CHANGED;
            };
        }
    };

    public abstract <T> T getBefore(IndexUpdateCommand<T> command);

    public abstract <T> T getAfter(IndexUpdateCommand<T> command);

    public abstract <T> UpdateMode mode(IndexUpdateCommand<T> command);
}
