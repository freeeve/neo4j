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
package org.neo4j.fleetmanagement.systemgraph;

import static org.neo4j.dbms.database.ComponentVersion.FLEET_MANAGEMENT_COMPONENT;
import static org.neo4j.fleetmanagement.systemgraph.FleetManagementVersion.CURRENT;

import java.util.Optional;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.database.AbstractSystemGraphComponent;
import org.neo4j.dbms.database.KnownSystemComponentVersion;
import org.neo4j.dbms.database.KnownSystemComponentVersions;
import org.neo4j.dbms.database.SystemGraphComponent;
import org.neo4j.dbms.database.SystemGraphComponentWithVersion;
import org.neo4j.fleetmanagement.systemgraph.versions.FleetManagementComponentVersion_0_202511;
import org.neo4j.fleetmanagement.systemgraph.versions.KnownFleetManagementComponentVersion;
import org.neo4j.fleetmanagement.systemgraph.versions.NoFleetManagementComponentVersion;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;

public class FleetManagementGraphComponent extends AbstractSystemGraphComponent
        implements SystemGraphComponentWithVersion {
    private final InternalLog log;
    private final KnownSystemComponentVersions<KnownFleetManagementComponentVersion>
            knownFleetManagementComponentVersions =
                    new KnownSystemComponentVersions<>(new NoFleetManagementComponentVersion());

    public FleetManagementGraphComponent(Config config, InternalLogProvider internalLogProvider) {
        super(config);
        this.log = internalLogProvider.getLog(getClass());

        var version0 = new FleetManagementComponentVersion_0_202511();
        knownFleetManagementComponentVersions.add(version0);
    }

    @Override
    public Name componentName() {
        return FLEET_MANAGEMENT_COMPONENT;
    }

    @Override
    public Status detect(Transaction tx) {
        Status status = null;
        Integer versionNumber = null;
        Optional<FleetManagementVersion> version = Optional.empty();
        try (var nodes = tx.findNodes(VERSION_LABEL)) {
            if (nodes.hasNext()) {
                Node versionNode = nodes.next();
                versionNumber =
                        (Integer) versionNode.getProperty(componentName().name(), null);
                if (versionNumber == null) {
                    status = Status.UNINITIALIZED;
                } else {
                    version = FleetManagementVersion.fromVersionNumber(versionNumber);
                    status = statusFromVersion(version.orElse(null));
                }
            } else {
                status = Status.UNINITIALIZED;
            }
        } catch (Exception e) {
            log.warn("Fleet management component version detection failure", e);
            status = Status.UNSUPPORTED_FUTURE;
        } finally {
            log.debug(
                    "Fleet management component version detection, verNo: %s, version: %s, status: %s",
                    versionNumber, version, status);
        }
        return status;
    }

    @Override
    protected void initializeSystemGraphModel(Transaction tx, GraphDatabaseService systemDb) {
        log.debug("Fleet management component initialized, desired version: %s", CURRENT);
        setCurrentVersion(tx);
    }

    @Override
    public void upgradeToCurrent(GraphDatabaseService systemDb) throws Exception {
        log.debug("Fleet management component upgrade, desired version: %s", CURRENT);
        SystemGraphComponent.executeWithFullAccess(systemDb, tx -> {
            var currentVersion = knownFleetManagementComponentVersions.detectCurrentComponentVersion(tx);
            knownFleetManagementComponentVersions
                    .latestComponentVersion()
                    .upgradeFleetGraph(tx, currentVersion.version);
        });
    }

    private void setCurrentVersion(Transaction tx) {
        KnownSystemComponentVersion.setVersionProperty(
                tx, FleetManagementVersion.CURRENT.getVersion(), componentName(), log);
    }

    private Status statusFromVersion(FleetManagementVersion version) {
        if (version == null) {
            return Status.UNSUPPORTED_FUTURE;
        }
        return version == CURRENT ? Status.CURRENT : Status.REQUIRES_UPGRADE;
    }

    @Override
    public int getLatestSupportedVersion() {
        return CURRENT.getVersion();
    }
}
