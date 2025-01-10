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
package org.neo4j.configuration.helpers;

public enum LogObfuscationLevel {
    ALL {
        @Override
        public boolean obfuscateLiterals() {
            return true;
        }

        @Override
        public boolean obfuscateOnlyUnsafeLiterals() {
            return false;
        }
    },
    NONE {
        @Override
        public boolean obfuscateLiterals() {
            return false;
        }

        @Override
        public boolean obfuscateOnlyUnsafeLiterals() {
            return false;
        }
    },
    ONLY_UNSAFE_LITERALS {
        @Override
        public boolean obfuscateLiterals() {
            return true;
        }

        @Override
        public boolean obfuscateOnlyUnsafeLiterals() {
            return true;
        }
    };

    public abstract boolean obfuscateLiterals();

    public abstract boolean obfuscateOnlyUnsafeLiterals();

    public static LogObfuscationLevel create(boolean obfuscateLiterals, boolean onlyUnsafeLiterals) {
        if (!obfuscateLiterals) {
            return NONE;
        } else if (onlyUnsafeLiterals) {
            return ONLY_UNSAFE_LITERALS;
        } else {
            return ALL;
        }
    }
}
