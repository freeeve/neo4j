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

import static org.assertj.core.api.Assertions.fail;

import org.assertj.core.api.InstanceOfAssertFactory;
import org.neo4j.bolt.fsm.StateMachineHandle;
import org.neo4j.bolt.fsm.error.StateMachineException;
import org.neo4j.function.ThrowingConsumer;

public final class StateMachineHandleAssertions
        extends AbstractStateMachineAssertions<StateMachineHandleAssertions, StateMachineHandle> {

    StateMachineHandleAssertions(StateMachineHandle stateMachine) {
        super(stateMachine, StateMachineHandleAssertions.class);
    }

    public static StateMachineHandleAssertions assertThat(StateMachineHandle fsm) {
        return new StateMachineHandleAssertions(fsm);
    }

    public static InstanceOfAssertFactory<StateMachineHandle, StateMachineHandleAssertions> stateMachine() {
        return new InstanceOfAssertFactory<>(StateMachineHandle.class, StateMachineHandleAssertions::new);
    }

    public StateMachineHandleAssertions shouldKillConnection(
            ThrowingConsumer<StateMachineHandle, StateMachineException> consumer) {
        this.isNotNull();

        try {
            consumer.accept(this.actual);
            fail("should have killed the connection");
        } catch (StateMachineException ignore) {
        }

        return this;
    }
}
