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
package org.neo4j.util;

import java.util.regex.Pattern;

public final class Stringifier {
    private static final Pattern UNICODE_ESCAPE_PATTERN = Pattern.compile("([^\\\\])(\\\\u[0-9]{4})");

    /*
     * Some strings (identifiers) were escaped with back-ticks to allow non-identifier characters
     * When printing these again, the knowledge of the back-ticks is lost, but the same test for
     * non-identifier characters can be used to recover that knowledge.
     */
    public static String backtick(String txt) {
        return backtick(txt, false, false, false);
    }

    public static String backtick(String txt, boolean alwaysBacktick) {
        return backtick(txt, alwaysBacktick, false, false);
    }

    public static String backtick(String txt, boolean alwaysBacktick, boolean globbing) {
        return backtick(txt, alwaysBacktick, globbing, false);
    }

    public static String backtick(String txt, boolean alwaysBacktick, boolean globbing, boolean backtickEmpty) {
        final var withoutGlobbing = globbing ? txt.replace('*', 'x').replace('?', 'x') : txt;
        final var needsBackticks = alwaysBacktick
                || (!txt.isEmpty() && !UnicodeHelper.isIdentifierInAllVersions(withoutGlobbing))
                || (backtickEmpty && txt.isEmpty());
        return needsBackticks ? addBackticks(txt) : txt;
    }

    private static String addBackticks(String txt) {
        String bt = txt.replace("`", "``");
        return "`" + UNICODE_ESCAPE_PATTERN.matcher(bt).replaceAll("$1\\\\$2") + "`";
    }
}
