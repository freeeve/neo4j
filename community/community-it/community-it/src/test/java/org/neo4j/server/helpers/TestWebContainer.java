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
package org.neo4j.server.helpers;

import static java.util.Objects.requireNonNull;
import static org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME;
import static org.neo4j.configuration.GraphDatabaseSettings.initial_default_database;

import java.net.URI;
import java.util.Optional;
import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.ssl.SslPolicyConfig;
import org.neo4j.configuration.ssl.SslPolicyScope;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.kernel.impl.factory.GraphDatabaseFacade;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.server.AbstractNeoWebServer;
import org.neo4j.server.http.cypher.TransactionRegistry;
import org.neo4j.ssl.config.SslPolicyProvider;
import org.neo4j.test.ssl.SelfSignedCertificateFactory;

public class TestWebContainer {
    private final DatabaseManagementService managementService;
    private final AbstractNeoWebServer neoWebServer;
    private final InternalLogProvider logProvider;

    public TestWebContainer(DatabaseManagementService managementService, InternalLogProvider logProvider) {
        requireNonNull(managementService);
        this.managementService = managementService;
        this.neoWebServer = getNeoWebServer(managementService);
        this.logProvider = logProvider;
    }

    public URI getBaseUri() {
        return neoWebServer.getBaseUri();
    }

    public Optional<URI> httpsUri() {
        return neoWebServer.httpsUri();
    }

    public void shutdown() {
        managementService.shutdown();
        logProvider.close();
    }

    public DatabaseManagementService getDatabaseManagementService() {
        return managementService;
    }

    public GraphDatabaseFacade getDefaultDatabase() {
        var config = getDependencyResolver(managementService).resolveDependency(Config.class);
        var defaultDatabase = config.get(initial_default_database);
        return (GraphDatabaseFacade) managementService.database(defaultDatabase);
    }

    public <T> T resolveDependency(Class<T> clazz) {
        return getDependencyResolver(managementService).resolveDependency(clazz);
    }

    public Config getConfig() {
        return neoWebServer.getConfig();
    }

    public TransactionRegistry getTransactionRegistry() {
        return neoWebServer.getTransactionRegistry();
    }

    public String getState() {
        return neoWebServer.getWebServer().getState();
    }

    private static AbstractNeoWebServer getNeoWebServer(DatabaseManagementService managementService) {
        return getDependencyResolver(managementService).resolveDependency(AbstractNeoWebServer.class);
    }

    private static DependencyResolver getDependencyResolver(DatabaseManagementService managementService) {
        return ((GraphDatabaseAPI) managementService.database(SYSTEM_DATABASE_NAME)).getDependencyResolver();
    }

    public void replaceHTTPSCertificate() throws Exception {
        var config = getConfig();
        SslPolicyConfig policy = SslPolicyConfig.forScope(SslPolicyScope.HTTPS);
        if (config.get(policy.enabled)) {
            var certDirectory = config.get(policy.base_directory);
            var fs = new DefaultFileSystemAbstraction();

            // we need to delete these files for SelfSignedCertificateFactory.create to work
            fs.deleteFile(config.get(policy.public_certificate));
            fs.deleteFile(config.get(policy.private_key));

            SelfSignedCertificateFactory.create(fs, certDirectory);

            var policyProvider = resolveDependency(SslPolicyProvider.class);
            policyProvider.reloadPolicies();
        }
    }
}
