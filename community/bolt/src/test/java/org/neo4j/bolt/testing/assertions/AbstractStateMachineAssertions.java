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
package org.neo4j.bolt.testing.assertions;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.neo4j.bolt.fsm.Context;
import org.neo4j.bolt.fsm.StateMachine;
import org.neo4j.bolt.fsm.state.StateReference;

public sealed class AbstractStateMachineAssertions<
                SELF extends AbstractStateMachineAssertions<SELF, FSM>, FSM extends StateMachine>
        extends AbstractAssert<SELF, FSM> permits StateMachineAssertions, StateMachineHandleAssertions {

    protected AbstractStateMachineAssertions(FSM actual, Class<SELF> selfType) {
        super(actual, selfType);
    }

    @SuppressWarnings("unchecked")
    public SELF stateSatisfies(Consumer<StateReference> assertions) {
        this.isNotNull();

        assertions.accept(((Context) this.actual).state());

        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    public SELF defaultStateSatisfies(Consumer<StateReference> assertions) {
        this.isNotNull();

        assertions.accept(this.actual.defaultState());

        return (SELF) this;
    }

    public SELF isInState(StateReference reference) {
        return this.stateSatisfies(state -> Assertions.assertThat(state)
                .as("is in state %s", reference.name())
                .isEqualTo(reference));
    }

    public SELF isNotInState(StateReference reference) {
        return this.stateSatisfies(state -> Assertions.assertThat(state)
                .as("is not in state %s", reference.name())
                .isEqualTo(reference));
    }

    public SELF hasDefaultState(StateReference reference) {
        return this.defaultStateSatisfies(state -> Assertions.assertThat(state)
                .as("is not configured with default state %s", reference.name())
                .isEqualTo(reference));
    }

    @SuppressWarnings("unchecked")
    public SELF isInterrupted() {
        this.isNotNull();

        if (!this.actual.isInterrupted()) {
            this.failWithMessage("Expected state machine to be interrupted");
        }

        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    public SELF isNotInterrupted() {
        this.isNotNull();

        if (this.actual.isInterrupted()) {
            this.failWithMessage("Expected state machine to not be interrupted");
        }

        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    public SELF hasFailed() {
        this.isNotNull();

        if (!this.actual.hasFailed()) {
            failWithMessage("Expected state machine to be marked failed");
        }

        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    public SELF hasNotFailed() {
        this.isNotNull();

        if (this.actual.hasFailed()) {
            failWithMessage("Expected state machine to not be marked failed");
        }

        return (SELF) this;
    }
}
