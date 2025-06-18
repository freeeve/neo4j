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
package org.neo4j.bolt.protocol.common.connector.transport;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.ChannelOption;

public final class ConnectorOption<T> {

    private final ChannelOption<T> option;

    public static ConnectorOption<Integer> TCP_FAST_OPEN = new ConnectorOption<>(ChannelOption.TCP_FASTOPEN);
    public static ConnectorOption<Boolean> TCP_FAST_OPEN_CONNECT =
            new ConnectorOption<>(ChannelOption.TCP_FASTOPEN_CONNECT);

    private ConnectorOption(ChannelOption<T> option) {
        this.option = option;
    }

    public void set(AbstractBootstrap<?, ?> bootstrap, T value) {
        bootstrap.option(this.option, value);
    }

    @Override
    public String toString() {
        return this.option.name();
    }
}
