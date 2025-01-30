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
package org.neo4j.dbms.systemgraph;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.neo4j.kernel.database.NamedDatabaseId.NAMED_SYSTEM_DATABASE_ID;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.neo4j.dbms.database.DatabaseContext;
import org.neo4j.dbms.database.DatabaseContextProvider;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.monitoring.DatabaseEventListeners;
import org.neo4j.logging.NullLog;

class ContextBasedSystemDatabaseProviderTest {
    @Test
    void shouldResetWhenDatabaseCreated() {
        var context = mock(DatabaseContext.class);
        var databaseFacade = mock(GraphDatabaseAPI.class);
        when(context.databaseFacade()).thenReturn(databaseFacade);
        var contextProvider = mock(DatabaseContextProvider.class);
        when(contextProvider.getDatabaseContext(any(NamedDatabaseId.class))).thenReturn(Optional.of(context));
        var listeners = new DatabaseEventListeners(NullLog.getInstance());
        var provider = new ContextBasedSystemDatabaseProvider(contextProvider, listeners);

        // when
        provider.database();
        provider.database();
        provider.database();

        // then
        verify(contextProvider, times(1)).getDatabaseContext(NAMED_SYSTEM_DATABASE_ID);
        verify(context, times(1)).databaseFacade();
        verifyNoMoreInteractions(context, contextProvider);
        clearInvocations(context, contextProvider);

        // when
        provider.database();
        provider.database();
        provider.database();

        // then
        verifyNoInteractions(context, contextProvider);

        // when
        listeners.databaseCreate(NAMED_SYSTEM_DATABASE_ID);
        provider.database();
        provider.database();
        provider.database();

        // then
        verify(contextProvider, times(1)).getDatabaseContext(NAMED_SYSTEM_DATABASE_ID);
        verify(context, times(1)).databaseFacade();
        verifyNoMoreInteractions(context, contextProvider);
    }
}
