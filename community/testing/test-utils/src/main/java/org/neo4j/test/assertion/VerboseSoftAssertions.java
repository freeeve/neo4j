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
package org.neo4j.test.assertion;

import static org.assertj.core.groups.FieldsOrPropertiesExtractor.extract;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Function;
import org.assertj.core.api.SoftAssertionError;
import org.assertj.core.api.SoftAssertions;

/**
 * See https://github.com/assertj/assertj/issues/864
 * We want to get stack traces of the individual assertion errors,
 * which does not seem to work if SoftAssertions succeeds instantiating a
 * `org.opentest4j.MultipleFailuresError`. So we circumvent that.
 */
public class VerboseSoftAssertions extends SoftAssertions {

    private static final Function<Throwable, String> ERROR_DESCRIPTION_EXTRACTOR = throwable -> {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        throwable.printStackTrace(ps);
        return baos.toString();
    };

    @Override
    public void assertAll() {
        List<AssertionError> errors = assertionErrorsCollected();
        if (!errors.isEmpty()) {
            throw new SoftAssertionError(extract(errors, ERROR_DESCRIPTION_EXTRACTOR));
        }
    }
}
