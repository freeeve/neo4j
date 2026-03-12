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
package org.neo4j.string;

public final class UnicodeData {
    static final char[] WHITESPACES = new char[] {
        '\t', //  character tabulation
        '\n', //  line feed
        '\u000B', //  line tabulation
        '\u000C', //  form feed
        '\r', //  carriage return
        ' ', //  space
        '\u0085', //  next line
        '\u00A0', //  no-break space
        '\u1680', //  ogham space mark
        '\u180E', //  Mongolian vowel separator
        '\u2000', //  en quad
        '\u2001', //  em quad
        '\u2002', //  en space
        '\u2003', //  em space
        '\u2004', //  three-per-em space
        '\u2005', //  four-per-em space
        '\u2006', //  six-per-em space
        '\u2007', //  figure space
        '\u2008', //  punctuation space
        '\u2009', //  thin space
        '\u200A', //  hair space
        '\u200B', //  zero width space
        '\u200C', //  zero width non-joiner
        '\u200D', //  zero width joiner
        '\u2028', //  line separator
        '\u2029', //  paragraph separator
        '\u202F', //  narrow no-break space
        '\u205F', //  medium mathematical space
        '\u2060', //  word joiner
        '\u3000', //  ideographic space
        '\uFEFF' //  zero width non-breaking space
    };
    static final char[] RND = new char[] {
        '\u1BE9', '\u20E8', '\u3A8A', '\u1B3F', '\u2DD9', '\u0229', '\u1956', '\u3468', '\u2134', '\u2CD1', '\u009E',
        '\u07E8', '\u14F1', '\u1365', '\u0B24', '\u21CA', '\u226F', '\u1FE2', '\u159F', '\u2524', '\u04A7', '\u3DE9',
        '\u0B5A', '\u36ED', '\u3166', '\u0E42', '\u2151', '\u04E7', '\u3305', '\u33E1', '\u045A', '\u21A6', '\u35CC',
        '\u3F39', '\u3808', '\u1AA7', '\u3F97', '\u3B97', '\u19B4', '\u0CB2', '\u2384', '\u13D8', '\u1FFD', '\u1F35',
        '\u3BD7', '\u0F5D', '\u069D', '\u2BF9', '\u0307', '\u17C3', '\u12C6', '\u2490', '\u232A', '\u3C6E', '\u082B',
        '\u190A', '\u3611', '\u2F6E', '\u30EE', '\u34B8', '\u0CE2', '\u1195', '\u2F3B', '\u0CAC', '\u1DCD', '\u0F26',
        '\u3B38', '\u0333', '\u003A', '\u13E4', '\u0850', '\u12B4', '\u166B', '\u31E3', '\u3C07', '\u2490', '\u2BD0',
        '\u2023', '\u061A', '\u2369', '\u38F2', '\u0FCB', '\u1F4B', '\u2ED1', '\u14C1', '\u2A86', '\u149E', '\u185C',
        '\u0ABB', '\u33EC', '\u1DAE', '\u3CFE', '\u198F', '\u07B0', '\u0567', '\u1207', '\u20F0', '\u2021', '\u1CC9',
        '\u3102'
    };

    private UnicodeData() {}
}
