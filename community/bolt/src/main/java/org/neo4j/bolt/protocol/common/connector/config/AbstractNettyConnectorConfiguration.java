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
package org.neo4j.bolt.protocol.common.connector.config;

import io.netty.handler.ssl.SslContext;
import java.util.Objects;
import javax.net.ssl.SSLException;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.connectors.BoltConnectorInternalSettings;
import org.neo4j.ssl.SslPolicy;
import org.neo4j.ssl.config.ScopedSslPolicyProvider;

public abstract class AbstractNettyConnectorConfiguration extends AbstractConnectorConfiguration
        implements NettyConnectorConfiguration {

    private final boolean enableMergeCumulator;
    private final boolean enableProxyProtocol;
    private final ScopedSslPolicyProvider sslPolicyProvider;

    private volatile SslPolicy lastReceivedPolicy;
    private volatile SslContext currentSslContext;

    public AbstractNettyConnectorConfiguration(AbstractFactory<?> builder) {
        super(builder);

        this.enableMergeCumulator = builder.enableMergeCumulator;
        this.enableProxyProtocol = builder.enableProxyProtocol;
        this.sslPolicyProvider = builder.sslPolicyProvider;
    }

    @Override
    public boolean enableMergeCumulator() {
        return this.enableMergeCumulator;
    }

    @Override
    public boolean enableProxyProtocol() {
        return this.enableProxyProtocol;
    }

    @Override
    public SslContext sslContext() {
        var policy = this.sslPolicyProvider.getPolicy();
        var sslContext = this.currentSslContext;

        var hasPolicyChanged = policy != this.lastReceivedPolicy;
        var hasContext = sslContext != null;
        var hasPolicy = policy != null;

        // skip execution if the policy has not changed, and we're either not supposed to have a context
        // or a context has previously been created
        if (!hasPolicyChanged && (!hasPolicy || hasContext)) {
            return sslContext;
        }

        // if no policy has been assigned, update the cached state (to shortcut execution later on)
        if (policy == null) {
            // TODO: Check whether requireEncryption is enabled and signal invalid configuration in that
            //       case
            this.lastReceivedPolicy = null;
            return null;
        }

        // create a new SslContext and update the policy - if this fails, we'll keep things as-is and
        // try again on the next invocation
        try {
            sslContext = policy.nettyServerContext();
            this.lastReceivedPolicy = policy;

            return sslContext;
        } catch (SSLException ex) {
            throw new IllegalStateException("Failed to load SSL policy for connector", ex);
        }
    }

    @SuppressWarnings("unchecked")
    public abstract static class AbstractFactory<SELF extends AbstractFactory<SELF>>
            extends AbstractConnectorConfiguration.AbstractFactory<SELF>
            implements NettyConnectorConfiguration.Factory<SELF> {

        private boolean enableMergeCumulator = true;
        private boolean enableProxyProtocol = false;
        private ScopedSslPolicyProvider sslPolicyProvider = ScopedSslPolicyProvider.getNullInstance();

        @Override
        public SELF fromConfig(Config config) {
            super.fromConfig(config);

            this.enableMergeCumulator = config.get(BoltConnectorInternalSettings.netty_message_merge_cumulator);
            this.enableProxyProtocol = config.get(BoltConnectorInternalSettings.proxy_protocol_enabled);

            return (SELF) this;
        }

        @Override
        public SELF enableMergeCumulator(boolean value) {
            this.enableMergeCumulator = value;
            return (SELF) this;
        }

        @Override
        public SELF enableProxyProtocol(boolean value) {
            this.enableProxyProtocol = value;
            return (SELF) this;
        }

        @Override
        public SELF sslPolicyProvider(ScopedSslPolicyProvider policyProvider) {
            Objects.requireNonNull(policyProvider, "SslPolicyProvider cannot be null");
            this.sslPolicyProvider = policyProvider;

            return (SELF) this;
        }
    }
}
