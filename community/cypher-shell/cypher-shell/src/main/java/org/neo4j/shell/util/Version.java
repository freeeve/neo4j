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
package org.neo4j.shell.util;

import static java.lang.String.format;

import java.util.OptionalInt;

@SuppressWarnings("WeakerAccess")
public record Version(int major, int minor, int patch, OptionalInt preRelease) implements Comparable<Version> {
    public Version(int major, int minor, int patch) {
        this(major, minor, patch, OptionalInt.empty());
    }

    @Override
    public int compareTo(Version o) {
        int comp = Integer.compare(major, o.major);
        if (comp == 0) {
            comp = Integer.compare(minor, o.minor);
            if (comp == 0) {
                comp = Integer.compare(patch, o.patch);
                if (comp == 0) {
                    if (preRelease.isEmpty()) comp = o.preRelease.isEmpty() ? 0 : -1;
                    else if (o.preRelease.isEmpty()) comp = 1;
                    else comp = Integer.compare(preRelease.getAsInt(), o.preRelease.getAsInt());
                }
            }
        }
        return comp;
    }

    @Override
    public String toString() {
        return preRelease.isPresent()
                ? format("%d.%d.%d-%d", major, minor, patch, preRelease.getAsInt())
                : format("%d.%d.%d", major, minor, patch);
    }

    public String majorMinorString() {
        return format("%d.%d", major, minor);
    }
}
