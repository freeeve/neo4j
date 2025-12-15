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
package org.neo4j.bolt.fsm;

import static org.neo4j.bolt.testing.util.ErrorUtil.useNewMessage;

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.neo4j.bolt.fsm.error.AdmissionControlException;
import org.neo4j.bolt.fsm.error.ConnectionTerminating;
import org.neo4j.bolt.fsm.error.NoSuchStateException;
import org.neo4j.bolt.fsm.error.StateMachineException;
import org.neo4j.bolt.fsm.error.state.IllegalRequestParameterException;
import org.neo4j.bolt.fsm.state.State;
import org.neo4j.bolt.fsm.state.StateReference;
import org.neo4j.bolt.protocol.common.connector.connection.ConnectionHandle;
import org.neo4j.bolt.protocol.common.fsm.error.AuthenticationStateTransitionException;
import org.neo4j.bolt.protocol.common.fsm.response.ResponseHandler;
import org.neo4j.bolt.protocol.common.message.Error;
import org.neo4j.bolt.protocol.common.message.request.RequestMessage;
import org.neo4j.bolt.security.error.AuthenticationException;
import org.neo4j.bolt.testing.assertions.ErrorAssertions;
import org.neo4j.bolt.testing.assertions.StateMachineHandleAssertions;
import org.neo4j.bolt.testing.mock.ConnectionMockFactory;
import org.neo4j.bolt.testing.mock.StateMockFactory;
import org.neo4j.dbms.admissioncontrol.AdmissionControlResponse;
import org.neo4j.dbms.admissioncontrol.AdmissionControlToken;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectAssertions;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.HasQuery;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.exceptions.Status.General;
import org.neo4j.kernel.api.exceptions.Status.HasStatus;
import org.neo4j.kernel.api.exceptions.Status.Request;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogAssertions;
import org.neo4j.logging.internal.SimpleLogService;

class StateMachineImplTest {

    private static final StateReference INITIAL_REFERENCE = new StateReference("initial");
    private static final StateReference TEST_REFERENCE = new StateReference("test");
    private static final StateReference DEFAULT_REFERENCE = new StateReference("default");
    private static final StateReference UNKNOWN_REFERENCE = new StateReference("unknown");
    private static final ErrorGqlStatusObject gqlDummy = ErrorGqlStatusObjectImplementation.from(
                    GqlStatusInfoCodes.STATUS_50N42)
            .build();

    private StateMachineImpl fsm;

    private ConnectionHandle connection;
    private StateMachineConfiguration configuration;
    private State initialState;

    private AssertableLogProvider userLog;
    private AssertableLogProvider internalLog;

    @BeforeEach
    void prepare() throws StateMachineException {
        this.connection = ConnectionMockFactory.newInstance();
        this.configuration = Mockito.mock(StateMachineConfiguration.class);
        this.initialState = StateMockFactory.newFactory(INITIAL_REFERENCE)
                .withResult(INITIAL_REFERENCE)
                .attachTo(this.configuration);

        this.userLog = new AssertableLogProvider();
        this.internalLog = new AssertableLogProvider();

        this.fsm = new StateMachineImpl(
                this.connection,
                this.configuration,
                new SimpleLogService(this.userLog, this.internalLog),
                this.initialState);
    }

    @Test
    void shouldIndicateParentConnection() {
        var connection = this.fsm.connection();

        Assertions.assertThat(connection).isSameAs(this.connection);
    }

    @Test
    void shouldIndicateParentConfiguration() {
        var configuration = this.fsm.configuration();

        Assertions.assertThat(configuration).isSameAs(this.configuration);
    }

    @Test
    void shouldIndicateInitialStateAsCurrentState() {
        StateMachineHandleAssertions.assertThat(this.fsm).isInState(INITIAL_REFERENCE);
    }

    @Test
    void shouldForwardLookupToConfiguration() throws NoSuchStateException {
        var someState = StateMockFactory.attachNewInstance(TEST_REFERENCE, this.configuration);

        Mockito.doThrow(noSuchState(UNKNOWN_REFERENCE)).when(this.configuration).lookup(UNKNOWN_REFERENCE);

        var result = this.fsm.lookup(TEST_REFERENCE);

        Assertions.assertThat(result).isSameAs(someState);

        Mockito.verify(this.configuration).lookup(TEST_REFERENCE);
        Mockito.verifyNoMoreInteractions(this.configuration);
    }

    @Test
    void shouldForwardLookupToConfigurationErrors() throws NoSuchStateException {
        var ex = noSuchState(UNKNOWN_REFERENCE);

        Mockito.doThrow(ex).when(this.configuration).lookup(UNKNOWN_REFERENCE);

        Assertions.assertThatExceptionOfType(NoSuchStateException.class)
                .isThrownBy(() -> this.fsm.lookup(UNKNOWN_REFERENCE))
                .isSameAs(ex);

        Mockito.verify(this.configuration).lookup(UNKNOWN_REFERENCE);
        Mockito.verifyNoMoreInteractions(this.configuration);
    }

    @Test
    void shouldIndicateInitialStateAsDefaultState() {
        StateMachineHandleAssertions.assertThat(this.fsm).hasDefaultState(INITIAL_REFERENCE);
    }

    @Test
    void shouldHandleInterrupts() {
        StateMachineHandleAssertions.assertThat(this.fsm).isNotInterrupted();

        this.fsm.interrupt();

        StateMachineHandleAssertions.assertThat(this.fsm).isInterrupted();
    }

    @Test
    void shouldHandleResetsFromInterrupts() throws StateMachineException {
        StateMockFactory.attachNewInstance(TEST_REFERENCE, this.configuration);

        Mockito.doReturn(TEST_REFERENCE).when(this.initialState).process(Mockito.any(), Mockito.any(), Mockito.any());

        this.fsm.process(Mockito.mock(RequestMessage.class), Mockito.mock(ResponseHandler.class), null);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(TEST_REFERENCE)
                .isNotInterrupted();

        this.fsm.interrupt();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(TEST_REFERENCE)
                .isInterrupted();

        this.fsm.reset();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .isNotInterrupted();
    }

    @Test
    void shouldHandleResetsFromFailure() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);

        StateMockFactory.newFactory(TEST_REFERENCE)
                .withResult(new IllegalRequestParameterException(gqlDummy, "Something went wrong!"))
                .attachTo(this.configuration);

        Mockito.doReturn(TEST_REFERENCE).when(this.initialState).process(Mockito.any(), Mockito.any(), Mockito.any());

        // advance to someState
        this.fsm.process(Mockito.mock(RequestMessage.class), Mockito.mock(ResponseHandler.class), null);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(TEST_REFERENCE)
                .hasNotFailed();

        // trigger failure (exception does not bubble up as it is status bearing)
        this.fsm.process(Mockito.mock(RequestMessage.class), responseHandler, null);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(TEST_REFERENCE)
                .hasFailed();

        Mockito.verify(responseHandler).onFailure(Mockito.notNull());

        this.fsm.reset();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .hasNotFailed();
    }

    @Test
    void shouldFailWithNoSuchStateExceptionWhenDefaultStateIsUnknown() throws NoSuchStateException {
        var ex = noSuchState(DEFAULT_REFERENCE);

        Mockito.doThrow(ex).when(this.configuration).lookup(DEFAULT_REFERENCE);

        Assertions.assertThatExceptionOfType(NoSuchStateException.class)
                .isThrownBy(() -> this.fsm.defaultState(DEFAULT_REFERENCE))
                .withMessage(useNewMessage("50N00: Internal exception raised No such statue: default")
                        .whenLegacyFallbackTo("No such state: default"))
                .isSameAs(ex);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .hasDefaultState(INITIAL_REFERENCE)
                .hasNotFailed();
    }

    @Test
    void shouldRevertToDefaultStateOnResetFromInterrupt() throws StateMachineException {
        StateMockFactory.attachNewInstance(DEFAULT_REFERENCE, this.configuration);
        StateMockFactory.attachNewInstance(TEST_REFERENCE, this.configuration);

        Mockito.doReturn(TEST_REFERENCE).when(this.initialState).process(Mockito.any(), Mockito.any(), Mockito.any());

        StateMachineHandleAssertions.assertThat(this.fsm).isInState(INITIAL_REFERENCE);

        this.fsm.defaultState(DEFAULT_REFERENCE);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .isNotInterrupted();

        this.fsm.process(Mockito.mock(RequestMessage.class), Mockito.mock(ResponseHandler.class), null);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(TEST_REFERENCE)
                .isNotInterrupted();

        this.fsm.interrupt();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(TEST_REFERENCE)
                .isInterrupted();

        this.fsm.reset();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(DEFAULT_REFERENCE)
                .isNotInterrupted();
    }

    @Test
    void shouldRevertToDefaultStateOnResetFromFailure() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);

        StateMockFactory.attachNewInstance(DEFAULT_REFERENCE, this.configuration);
        StateMockFactory.newFactory(TEST_REFERENCE)
                .withResult(new IllegalRequestParameterException(gqlDummy, "Something went wrong!"))
                .attachTo(this.configuration);

        Mockito.doReturn(TEST_REFERENCE).when(this.initialState).process(Mockito.any(), Mockito.any(), Mockito.any());

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .hasNotFailed();

        this.fsm.defaultState(DEFAULT_REFERENCE);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .hasNotFailed();

        this.fsm.process(Mockito.mock(RequestMessage.class), Mockito.mock(ResponseHandler.class), null);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(TEST_REFERENCE)
                .hasNotFailed();

        this.fsm.process(Mockito.mock(RequestMessage.class), responseHandler, null);

        Mockito.verify(responseHandler).onFailure(Mockito.notNull());

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(TEST_REFERENCE)
                .hasFailed();

        this.fsm.reset();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(DEFAULT_REFERENCE)
                .hasNotFailed()
                .isNotInterrupted();
    }

    @Test
    @SuppressWarnings("removal")
    void shouldIgnoreRequestsWhileInterrupted() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);
        var request = Mockito.mock(RequestMessage.class);

        Mockito.doReturn(true).when(request).isIgnoredWhenFailed();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .isNotInterrupted();

        this.fsm.interrupt();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .isInterrupted();

        this.fsm.process(request, responseHandler, null);

        var inOrder = Mockito.inOrder(this.initialState, responseHandler);

        inOrder.verify(responseHandler).onIgnored();

        this.fsm.reset();

        this.fsm.process(request, responseHandler, null);

        inOrder.verify(this.initialState).process(Mockito.notNull(), Mockito.notNull(), Mockito.same(responseHandler));
        inOrder.verify(responseHandler).onSuccess();
    }

    @Test
    @SuppressWarnings("removal")
    void shouldIgnoreRequestsWhileFailed() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);
        var request = Mockito.mock(RequestMessage.class);

        Mockito.doThrow(new IllegalRequestParameterException(gqlDummy, "Something went wrong!"))
                .when(this.initialState)
                .process(Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doReturn(true).when(request).isIgnoredWhenFailed();

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .hasNotFailed();

        this.fsm.process(request, responseHandler, null);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .hasFailed();

        var inOrder = Mockito.inOrder(this.initialState, responseHandler);

        inOrder.verify(this.initialState).reference();
        inOrder.verify(this.initialState).process(Mockito.notNull(), Mockito.notNull(), Mockito.same(responseHandler));
        inOrder.verify(responseHandler).onFailure(Mockito.notNull());

        this.fsm.process(request, responseHandler, null);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .hasFailed();

        inOrder.verify(responseHandler).onIgnored();

        Mockito.doReturn(INITIAL_REFERENCE)
                .when(this.initialState)
                .process(Mockito.any(), Mockito.any(), Mockito.any());

        this.fsm.reset();
        this.fsm.process(Mockito.mock(RequestMessage.class), responseHandler, null);

        StateMachineHandleAssertions.assertThat(this.fsm)
                .isInState(INITIAL_REFERENCE)
                .hasNotFailed();

        inOrder.verify(this.initialState).process(Mockito.notNull(), Mockito.notNull(), Mockito.same(responseHandler));
        inOrder.verify(responseHandler).onSuccess();
    }

    @Test
    void shouldNotifyResponseHandlerOnSuccess() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);
        var request = Mockito.mock(RequestMessage.class);

        this.fsm.process(request, responseHandler, null);

        var inOrder = Mockito.inOrder(this.initialState, responseHandler);

        inOrder.verify(this.initialState)
                .process(Mockito.notNull(), Mockito.same(request), Mockito.same(responseHandler));
        inOrder.verify(responseHandler).onSuccess();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void shouldNotifyResponseHandlerOnFailure() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);
        var request = Mockito.mock(RequestMessage.class);

        Mockito.doThrow(new IllegalRequestParameterException(gqlDummy, "Something went wrong!"))
                .when(this.initialState)
                .process(Mockito.any(), Mockito.any(), Mockito.any());

        this.fsm.process(request, responseHandler, null);

        var captor = ArgumentCaptor.forClass(Error.class);
        var inOrder = Mockito.inOrder(this.initialState, responseHandler);

        inOrder.verify(this.initialState)
                .process(Mockito.notNull(), Mockito.same(request), Mockito.same(responseHandler));
        inOrder.verify(responseHandler).onFailure(captor.capture());

        ErrorAssertions.assertThat(captor.getValue())
                .hasStatus(Request.InvalidFormat)
                .hasMessage(useNewMessage("50N42: Unexpected error has occurred. See debug log for details.")
                        .whenLegacyFallbackTo("Something went wrong!"))
                .hasCauseInstanceOf(IllegalRequestParameterException.class);

        LogAssertions.assertThat(this.userLog).doesNotContainMessage("Client triggered an unexpected error");
        LogAssertions.assertThat(this.internalLog).doesNotContainMessage("Client triggered an unexpected error");
    }

    @Test
    void shouldFailWithNoSuchStateExceptionWhenNextStateIsUnknown() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);

        var ex = noSuchState(TEST_REFERENCE);

        Mockito.doThrow(ex).when(this.configuration).lookup(TEST_REFERENCE);
        Mockito.doReturn(TEST_REFERENCE).when(this.initialState).process(Mockito.any(), Mockito.any(), Mockito.any());

        Assertions.assertThatExceptionOfType(NoSuchStateException.class)
                .isThrownBy(() -> this.fsm.process(Mockito.mock(RequestMessage.class), responseHandler, null))
                .withMessage(useNewMessage("50N00: Internal exception raised No such statue: test")
                        .whenLegacyFallbackTo("No such state: test"))
                .withNoCause();

        var captor = ArgumentCaptor.forClass(Error.class);
        Mockito.verify(responseHandler).onFailure(captor.capture());

        ErrorAssertions.assertThat(captor.getValue())
                .hasStatus(General.UnknownError)
                .hasMessage(useNewMessage("50N00: Internal exception raised No such statue: test")
                        .whenLegacyFallbackTo("No such state: test"))
                .hasCauseInstanceOf(NoSuchStateException.class);
    }

    @Test
    void shouldLogDatabaseErrors() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);

        Mockito.doThrow(new MockDatabaseException("Something went wrong!"))
                .when(this.initialState)
                .process(Mockito.any(), Mockito.any(), Mockito.any());

        this.fsm.process(Mockito.mock(RequestMessage.class), responseHandler, null);

        LogAssertions.assertThat(this.userLog)
                .containsMessages("Client triggered an unexpected error", "DatabaseError");

        LogAssertions.assertThat(this.internalLog)
                .containsMessages("Client triggered an unexpected error", "DatabaseError");

        Mockito.verify(this.initialState).process(Mockito.notNull(), Mockito.notNull(), Mockito.same(responseHandler));

        Mockito.verify(responseHandler).onFailure(Mockito.notNull());
    }

    @Test
    void shouldLogDatabaseErrorsWithQueryId() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);

        Mockito.doThrow(new QueryIdMockDatabaseException("Something went wrong!", 42))
                .when(this.initialState)
                .process(Mockito.any(), Mockito.any(), Mockito.any());

        this.fsm.process(Mockito.mock(RequestMessage.class), responseHandler, null);

        LogAssertions.assertThat(this.userLog)
                .containsMessages("Client triggered an unexpected error", "DatabaseError", "queryId");

        LogAssertions.assertThat(this.internalLog).containsMessages("DatabaseError", "queryId");

        Mockito.verify(this.initialState).process(Mockito.notNull(), Mockito.notNull(), Mockito.same(responseHandler));

        Mockito.verify(responseHandler).onFailure(Mockito.notNull());
    }

    @Test
    void shouldRethrowAuthenticationStateTransitionExceptions() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);
        var ex = AuthenticationStateTransitionException.wrapError(AuthenticationException.internalError(
                this.getClass().getSimpleName(), "Something went wrong", Request.InvalidUsage));

        Mockito.doThrow(ex).when(this.initialState).process(Mockito.any(), Mockito.any(), Mockito.any());

        Assertions.assertThatExceptionOfType(AuthenticationStateTransitionException.class)
                .isThrownBy(() -> this.fsm.process(Mockito.mock(RequestMessage.class), responseHandler, null))
                .isSameAs(ex);

        Mockito.verify(this.initialState).process(Mockito.notNull(), Mockito.notNull(), Mockito.same(responseHandler));
        Mockito.verify(responseHandler).onFailure(Mockito.notNull());
    }

    @Test
    void shouldRethrowStateMachineExceptionWithoutState() throws StateMachineException {
        var responseHandler = Mockito.mock(ResponseHandler.class);
        var ex = new MockStateMachineException("Something went wrong");

        Mockito.doThrow(ex).when(this.initialState).process(Mockito.any(), Mockito.any(), Mockito.any());

        Assertions.assertThatExceptionOfType(MockStateMachineException.class)
                .isThrownBy(() -> this.fsm.process(Mockito.mock(RequestMessage.class), responseHandler, null))
                .isSameAs(ex);

        Mockito.verify(this.initialState).process(Mockito.notNull(), Mockito.notNull(), Mockito.same(responseHandler));
        Mockito.verify(responseHandler).onFailure(Mockito.any());
    }

    @Test
    @SuppressWarnings("removal")
    void shouldAwaitAdmissionControl() throws StateMachineException {
        var request = Mockito.mock(RequestMessage.class);
        var responseHandler = Mockito.mock(ResponseHandler.class);
        var token = Mockito.mock(AdmissionControlToken.class);

        Mockito.doReturn(AdmissionControlResponse.RELEASED).when(token).await();

        this.fsm.process(request, responseHandler, token);

        Mockito.verify(token, Mockito.times(1)).await();
        Mockito.verify(responseHandler, Mockito.times(1)).onSuccess();
    }

    @ParameterizedTest
    @MethodSource("responses")
    void shouldFailWhenAdmissionControlResponseImpliesFailure(AdmissionControlResponse response)
            throws StateMachineException {
        var request = Mockito.mock(RequestMessage.class);
        var responseHandler = Mockito.mock(ResponseHandler.class);
        var token = Mockito.mock(AdmissionControlToken.class);

        Mockito.doReturn(response).when(token).await();

        this.fsm.process(request, responseHandler, token);

        Mockito.verify(token, Mockito.times(1)).await();

        var captor = ArgumentCaptor.forClass(Error.class);
        Mockito.verify(responseHandler, Mockito.times(1)).onFailure(captor.capture());

        Error captorError = captor.getValue();
        ErrorAssertions.assertThat(captorError).hasStatus(Request.ResourceExhaustion);

        Throwable wrappedThrowable = captorError.wrappedThrowable();

        Assertions.assertThat(wrappedThrowable).isInstanceOf(AdmissionControlException.class);
        ErrorGqlStatusObjectAssertions.assertThat((AdmissionControlException) wrappedThrowable)
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_51N59)
                .hasStatusDescription(
                        "error: system configuration or operation exception - internal resource exhaustion. "
                                + "The DBMS is unable to handle the request, please retry later or contact the system operator. "
                                + "More information is present in the logs.");
    }

    public static Stream<AdmissionControlResponse> responses() {
        return Stream.of(
                AdmissionControlResponse.ADMISSION_CONTROL_PROCESS_STOPPED,
                AdmissionControlResponse.UNABLE_TO_ALLOCATE_NEW_TOKEN,
                AdmissionControlResponse.NO_TENANT_CREDIT);
    }

    private NoSuchStateException noSuchState(StateReference reference) {
        return NoSuchStateException.invalidServerStateTransition("No such statue", reference.name(), reference);
    }

    private class MockDatabaseException extends StateMachineException implements HasStatus {

        public MockDatabaseException(String message) {
            super(message);
        }

        @Override
        public Status status() {
            return Status.Transaction.TransactionStartFailed;
        }
    }

    private class QueryIdMockDatabaseException extends MockDatabaseException implements HasQuery {
        private final long queryId;

        public QueryIdMockDatabaseException(String message, long queryId) {
            super(message);
            this.queryId = queryId;
        }

        @Override
        public Long query() {
            return this.queryId;
        }

        @Override
        public void setQuery(Long queryId) {
            throw new UnsupportedOperationException();
        }
    }

    private class MockStateMachineException extends StateMachineException implements ConnectionTerminating {

        public MockStateMachineException(String message) {
            super(message);
        }
    }
}
