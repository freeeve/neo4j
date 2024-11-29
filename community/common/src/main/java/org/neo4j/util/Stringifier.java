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

import org.neo4j.internal.helpers.Strings;

public final class Stringifier {

    /*
     * Some strings (identifiers) were escaped with back-ticks to allow non-identifier characters
     * When printing these again, the knowledge of the back-ticks is lost, but the same test for
     * non-identifier characters can be used to recover that knowledge.
     */
    public static String backtick(String txt) {
        return backtick(txt, false, false);
    }

    public static String backtick(String txt, boolean alwaysBacktick) {
        return backtick(txt, alwaysBacktick, false);
    }

    public static String backtick(String txt, boolean alwaysBacktick, boolean globbing) {
        if (alwaysBacktick) {
            return "`" + escaped(txt) + "`";
        } else {
            boolean isJavaIdentifier = Strings.codePoints(txt)
                            .limit(1)
                            .allMatch(p -> UnicodeHelper.isIdentifierStart(p, CypherVersion.Cypher25)
                                    || orGlobbedCharacter(globbing, p))
                    && Strings.codePoints(txt)
                            .skip(1)
                            .allMatch(p -> UnicodeHelper.isIdentifierPart(p, CypherVersion.Cypher25)
                                    || orGlobbedCharacter(globbing, p));
            if (!isJavaIdentifier) {
                return "`" + escaped(txt) + "`";
            } else {
                return txt;
            }
        }
    }

    private static String escaped(String txt) {
        return txt.replace("`", "``");
    }

    private static boolean orGlobbedCharacter(boolean globbing, int p) {
        return globbing && ((p == (int) '*') || (p == (int) '?'));
    }
}
