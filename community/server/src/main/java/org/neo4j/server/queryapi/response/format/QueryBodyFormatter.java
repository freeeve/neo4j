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
package org.neo4j.server.queryapi.response.format;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import org.neo4j.driver.Bookmark;
import org.neo4j.driver.Result;
import org.neo4j.driver.summary.ResultSummary;

public class QueryBodyFormatter {
    private final DriverResultSerializer serializer;
    private State state;

    public QueryBodyFormatter(JsonGenerator jsonGenerator) {
        this.serializer = new DriverResultSerializer(jsonGenerator);
        this.state = State.CREATED;
    }

    public void json(FormatterConsumer<JsonBodyFormatter> consumer) throws IOException {
        if (state != State.CREATED) {
            throw new IllegalStateException(
                    "This method is called only once and can't be used in combination with others");
        }
        try {
            this.serializer.write(() -> consumer.accept(new JsonBodyFormatter(this.serializer)));
        } finally {
            state = State.FINISHED;
        }
    }

    public static class JsonBodyFormatter {
        private final DriverResultSerializer serializer;

        private JsonBodyFormatter(DriverResultSerializer serializer) {
            this.serializer = serializer;
        }

        public void data(Result result) throws IOException {
            this.serializer.writeData(result);
        }

        public void metadata(ResultSummary resultSummary, Set<Bookmark> bookmarks, boolean requireCounters)
                throws IOException {
            metadata(resultSummary, bookmarks, null, null, requireCounters);
        }

        public void metadata(
                ResultSummary resultSummary,
                Set<Bookmark> bookmarks,
                String txId,
                Instant timeout,
                boolean requireCounters)
                throws IOException {
            this.serializer.writeMetadata(resultSummary, bookmarks, txId, timeout, requireCounters);
        }
    }

    public interface FormatterConsumer<T> {
        void accept(T formatter) throws IOException;
    }

    private enum State {
        CREATED,
        FINISHED,
    }
}
