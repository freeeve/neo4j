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
package org.neo4j.bolt.testing.assertions.discovery;

import io.netty.buffer.ByteBuf;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.AbstractAssert;
import org.neo4j.bolt.testing.client.discovery.BeaconListener;
import org.neo4j.bolt.testing.client.discovery.DiscoveryTestClient;

public final class DiscoveryAssertions extends AbstractAssert<DiscoveryAssertions, DiscoveryTestClient> {

    private DiscoveryAssertions(DiscoveryTestClient discoveryTestClient) {
        super(discoveryTestClient, DiscoveryAssertions.class);
    }

    public static DiscoveryAssertions assertThat(DiscoveryTestClient client) {
        return new DiscoveryAssertions(client);
    }

    public void receivesBeaconSignal(Duration listenDuration, BeaconSignalAssertions assertions)
            throws InterruptedException {
        var future = new CompletableFuture<Void>();

        this.actual.listenForSignal(listenDuration, new BeaconListener() {
            @Override
            public void onBeaconSignal(InetSocketAddress sender, ByteBuf beacon) {
                try {
                    assertions.verifySignal(beacon);
                    future.complete(null);
                } catch (AssertionError | RuntimeException e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onComplete() {
                future.completeExceptionally(
                        new AssertionError("No valid beacon signal received within reasonable time period"));
            }
        });

        try {
            future.get(5, TimeUnit.MINUTES);
        } catch (TimeoutException ex) {
            throw new AssertionError("No valid beacon signal received within reasonable time period");
        } catch (ExecutionException ex) {
            var cause = ex.getCause();

            if (cause instanceof AssertionError assertionError) {
                throw assertionError;
            } else if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                throw new AssertionError("Error caught in test client", ex);
            }
        }
    }

    private static final class VerificationListener implements BeaconListener {
        private final BeaconSignalAssertions assertions;

        private Instant lastSignalTime = null;
        private final List<Duration> signalPeriods = new ArrayList<>();

        public VerificationListener(BeaconSignalAssertions assertions) {
            this.assertions = assertions;
        }

        @Override
        public void onBeaconSignal(InetSocketAddress sender, ByteBuf beacon) {
            var signalTime = Instant.now();
            var lastSignalTime = this.lastSignalTime;
            if (lastSignalTime != null) {
                this.signalPeriods.add(Duration.between(lastSignalTime, signalTime));
            }
            this.lastSignalTime = signalTime;

            assertions.verifySignal(beacon);
        }

        @Override
        public void onComplete() {
            if (this.signalPeriods.isEmpty()) {
                assertions.verifyPeriod(Duration.ZERO);
                return;
            }

            var millisTotal = 0L;
            for (var duration : this.signalPeriods) {
                millisTotal += duration.toMillis();
            }

            var average = Duration.ofMillis(millisTotal / this.signalPeriods.size());
            assertions.verifyPeriod(Duration.ZERO);
        }
    }
}
