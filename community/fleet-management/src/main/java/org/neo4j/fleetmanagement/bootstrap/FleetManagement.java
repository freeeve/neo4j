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
package org.neo4j.fleetmanagement.bootstrap;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.identity.ServerIdentity;
import org.neo4j.fleetmanagement.FleetManagementSettings;
import org.neo4j.fleetmanagement.communication.ConfigService;
import org.neo4j.fleetmanagement.communication.ConnectService;
import org.neo4j.fleetmanagement.communication.MetricsService;
import org.neo4j.fleetmanagement.communication.PingService;
import org.neo4j.fleetmanagement.communication.TopologyService;
import org.neo4j.fleetmanagement.communication.upstream.Upstream;
import org.neo4j.fleetmanagement.configuration.ClusterSync;
import org.neo4j.fleetmanagement.configuration.Configuration;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.procedures.MetricNamesSupplier;
import org.neo4j.fleetmanagement.procedures.Neo4jConfigNamesSupplier;
import org.neo4j.fleetmanagement.transactions.ITransactor;
import org.neo4j.fleetmanagement.utils.Logger;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.impl.factory.DbmsInfo;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;

public class FleetManagement extends LifecycleAdapter {
    private ITransactor transactor;
    private MainService mainService;
    private Log log;
    private ScheduledExecutorService scheduler;
    private State state;
    private Configuration configuration;
    private ConfigService configService;

    public FleetManagement(
            LogService logService,
            DatabaseManagementService databaseManagementService,
            Config config,
            DbmsInfo dbmsInfo,
            FileSystemAbstraction fs,
            ServerIdentity serverIdentity,
            State state) {

        if (!config.get(FleetManagementSettings.fleet_manager_enabled)) {
            // Stop immediately if disabled
            return;
        }

        try {
            var neo4jLog = logService.getUserLog(FleetManagement.class);
            Logger.initLogger(neo4jLog);
            this.log = Logger.getNeo4jLogger();
            this.state = state;
            this.configuration = new Configuration();
            MetricNamesSupplier.setConfiguration(this.configuration);
            Neo4jConfigNamesSupplier.setConfiguration(this.configuration);
            // Need to explicitly specify constructor arg types because reflection will not pick the interface type
            this.transactor = Reflection.getConstructorOfImplementationOf(
                            ITransactor.class, DbmsInfo.class, ServerIdentity.class, State.class)
                    .newInstance(dbmsInfo, serverIdentity, this.state);
            this.transactor.init(databaseManagementService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        scheduler = Executors.newScheduledThreadPool(1, runnable -> {
            var thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("neo4j.FleetManagement");
            thread.setUncaughtExceptionHandler((t, e) -> Logger.getFleetManagerLogger()
                    .debug(String.format(
                            "Uncaught exception in FleetManagement thread: %s", ExceptionUtils.getStackTrace(e))));
            return thread;
        });

        var upstream = new Upstream(this.transactor, this.log, config, this.state);
        var connectService = new ConnectService(
                config, fs, this.transactor, serverIdentity, upstream, this.state, this.configuration);
        var reportingService = new TopologyService(config, fs, this.transactor, serverIdentity, upstream, this.state);
        var metricsService =
                new MetricsService(this.transactor, serverIdentity, upstream, config, this.state, this.configuration);
        this.configService =
                new ConfigService(this.transactor, serverIdentity, upstream, config, this.state, this.configuration);
        var pingService =
                new PingService(config, fs, this.transactor, upstream, serverIdentity, this.state, this.configuration);

        var clusterSync = new ClusterSync(this.transactor, upstream, this.state);

        this.mainService = new MainService(
                this.transactor,
                upstream,
                reportingService,
                metricsService,
                clusterSync,
                scheduler,
                connectService,
                pingService,
                this.state,
                this.configuration);
    }

    @Override
    public void start() {
        if (mainService == null) {
            return;
        }
        configService.start();
        mainService.start();
    }

    @Override
    public void stop() {
        if (mainService == null) {
            return;
        }
        state.removePropertyChangeListeners();
        mainService.stop();
    }

    @Override
    public void shutdown() {
        if (mainService == null) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
