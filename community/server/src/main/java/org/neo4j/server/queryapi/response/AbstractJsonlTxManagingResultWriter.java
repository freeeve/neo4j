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
package org.neo4j.server.queryapi.response;

import com.fasterxml.jackson.core.JsonFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import org.neo4j.logging.InternalLog;
import org.neo4j.server.http.cypher.format.DefaultJsonFactory;
import org.neo4j.server.http.cypher.format.api.ConnectionException;
import org.neo4j.server.queryapi.exception.ExceptionsUnwrapper;
import org.neo4j.server.queryapi.request.TxManagedResultContainer;
import org.neo4j.server.queryapi.response.format.QueryAPICodec;
import org.neo4j.server.queryapi.response.format.QueryBodyFormatter;
import org.neo4j.server.queryapi.response.format.View;
import org.neo4j.server.queryapi.tx.TransactionManager;

abstract class AbstractJsonlTxManagingResultWriter implements MessageBodyWriter<TxManagedResultContainer> {

    private final InternalLog log;
    private final JsonFactory jsonFactory;
    private final TransactionManager transactionManager;

    protected AbstractJsonlTxManagingResultWriter(InternalLog log, View view, TransactionManager transactionManager) {
        this.log = log;
        this.jsonFactory = DefaultJsonFactory.INSTANCE.get().copy().setCodec(new QueryAPICodec(view));
        this.transactionManager = transactionManager;
    }

    @Override
    public void writeTo(
            TxManagedResultContainer container,
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream)
            throws IOException, WebApplicationException {
        httpHeaders.add("Transfer-encoding", "chunked");
        var hasFailed = true;
        var jsonGenerator = jsonFactory.createGenerator(entityStream);
        var formatter = new QueryBodyFormatter(jsonGenerator, entityStream).jsonl();
        try {
            var result = container.transaction().retrieveResults();
            var keys = result != null ? result.keys() : null;
            formatter.header(keys);
            if (result != null) {
                while (result.hasNext()) {
                    formatter.record(result.next());
                    entityStream.flush();
                }
            }
            if (container.requiresCommit()) {
                var bookmarks = container.transaction().commit();
                formatter.summary(
                        container.transaction().resultSummary(), bookmarks, container.requireSummaryCounters());
            } else {
                container.transaction().extendTimeout();
                formatter.summary(
                        container.transaction().resultSummary(),
                        null,
                        container.transaction().id(),
                        container.transaction().expiresAt(),
                        container.requireSummaryCounters());
            }

            hasFailed = false;
        } catch (IOException ex) {
            ExceptionsUnwrapper.unwrapAndThrowNeo4jAndQueryApiExceptions(ex);
            throw new ConnectionException("Failed to write to the connection", ex);
        } finally {
            if (!container.transaction().isOpen() || hasFailed) {
                transactionManager.removeTransaction(container.transaction().id());
            } else {
                transactionManager.releaseTransaction(container.transaction().id());
            }
            jsonGenerator.flush();
        }
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return TxManagedResultContainer.class.isAssignableFrom(type);
    }
}
