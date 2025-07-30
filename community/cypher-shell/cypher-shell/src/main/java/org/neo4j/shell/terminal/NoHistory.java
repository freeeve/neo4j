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
package org.neo4j.shell.terminal;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.ListIterator;
import org.jline.reader.History;
import org.jline.reader.LineReader;

class NoHistory implements History {
    @Override
    public Iterator<Entry> reverseIterator(int index) {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Entry> reverseIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public ListIterator<Entry> iterator() {
        return Collections.emptyListIterator();
    }

    @Override
    public boolean isPersistable(Entry entry) {
        return false;
    }

    @Override
    public void add(String line) {}

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public void resetIndex() {}

    @Override
    public void moveToEnd() {}

    @Override
    public boolean moveTo(int index) {
        return false;
    }

    @Override
    public boolean moveToLast() {
        return false;
    }

    @Override
    public boolean moveToFirst() {
        return false;
    }

    @Override
    public boolean next() {
        return false;
    }

    @Override
    public boolean previous() {
        return false;
    }

    @Override
    public String current() {
        return "";
    }

    @Override
    public ListIterator<Entry> iterator(int index) {
        return Collections.emptyListIterator();
    }

    @Override
    public void add(Instant time, String line) {}

    @Override
    public String get(int index) {
        throw new IllegalArgumentException("Empty history");
    }

    @Override
    public int last() {
        return -1;
    }

    @Override
    public int first() {
        return 0;
    }

    @Override
    public int index() {
        return 0;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void purge() {}

    @Override
    public void read(Path file, boolean checkDuplicates) {}

    @Override
    public void append(Path file, boolean incremental) {}

    @Override
    public void write(Path file, boolean incremental) {}

    @Override
    public void save() {}

    @Override
    public void load() {}

    @Override
    public void attach(LineReader reader) {}
}
