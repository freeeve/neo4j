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
package org.neo4j.bolt.discovery.config;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.neo4j.bolt.discovery.info.DiscoveryInformationProvider;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.connectors.BoltConnectorInternalSettings;

public final class DiscoveryConfiguration {

    private final DiscoveryInformationProvider infoProvider;
    private final int port;
    private final List<InetAddress> addresses;
    private final long broadcastInterval;
    private final float jitterPercentage;

    private DiscoveryConfiguration(
            DiscoveryInformationProvider infoProvider,
            int port,
            List<InetAddress> addresses,
            long broadcastInterval,
            float jitterPercentage) {
        this.infoProvider = infoProvider;
        this.port = port;
        this.addresses = new ArrayList<>(addresses);
        this.broadcastInterval = broadcastInterval;
        this.jitterPercentage = jitterPercentage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public DiscoveryInformationProvider infoProvider() {
        return this.infoProvider;
    }

    public int port() {
        return this.port;
    }

    public List<InetAddress> addresses() {
        return this.addresses;
    }

    public long broadcastInterval() {
        return this.broadcastInterval;
    }

    public float jitterPercentage() {
        return this.jitterPercentage;
    }

    public long effectiveBroadcastInterval(Random rng) {
        // jitter adds slight randomness to the broadcast interval and is given as a percentage of
        // inaccuracy (both in the positive and negative direction)
        var jitter = (long) (this.broadcastInterval * this.jitterPercentage);

        // if jitter is zero (either through configuration or unit limitations), it is ignored instead
        if (jitter < 1) {
            return this.broadcastInterval;
        }

        var offset = rng.nextLong(jitter * 2) - jitter;

        return this.broadcastInterval + offset;
    }

    public static final class Builder {

        private int port = 0;
        private List<InetAddress> addresses = new ArrayList<>();
        private long broadcastInterval = 30_000;
        private float jitterPercentage = .25f;

        private Builder() {}

        public DiscoveryConfiguration build(DiscoveryInformationProvider infoProvider) {
            return new DiscoveryConfiguration(
                    infoProvider, this.port, this.addresses, this.broadcastInterval, this.jitterPercentage);
        }

        public Builder fromConfig(Config config) {
            this.port = config.get(BoltConnectorInternalSettings.discovery_listen_port);
            this.broadcastInterval = config.get(BoltConnectorInternalSettings.discovery_broadcast_interval)
                    .toMillis();
            this.jitterPercentage = config.get(BoltConnectorInternalSettings.discovery_broadcast_jitter) / 100f;

            return this;
        }

        public Builder withPort(int port) {
            this.port = port;
            return this;
        }

        public Builder withAddresses(List<InetAddress> addresses) {
            this.addresses = new ArrayList<>(addresses);
            return this;
        }

        public Builder withBroadcastInterval(long broadcastInterval) {
            this.broadcastInterval = broadcastInterval;
            return this;
        }

        public Builder withJitterPercentage(float jitterPercentage) {
            this.jitterPercentage = jitterPercentage;
            return this;
        }
    }
}
