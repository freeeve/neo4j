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
package org.neo4j.dbms.systemgraph.versions;

import static org.neo4j.dbms.systemgraph.CommunityTopologyGraphVersion.COMMUNITY_TOPOLOGY_202502;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_DEFAULT_LANGUAGE_PROPERTY;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_LABEL;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_NAME_LABEL;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_NAME_PROPERTY;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DEFAULT_NAMESPACE;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.NAMESPACE_PROPERTY;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.QUOTED_DISPLAY_NAME_PROPERTY;

import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.graphdb.Transaction;
import org.neo4j.util.Stringifier;

/**
 * This is the CommunityTopologyComponent version for Neo4j 2025.02
 */
public class CommunityTopologyComponentVersion_3_202502 extends KnownCommunityTopologyComponentVersion {

    private final KnownCommunityTopologyComponentVersion previous;

    public CommunityTopologyComponentVersion_3_202502(KnownCommunityTopologyComponentVersion previous) {
        super(COMMUNITY_TOPOLOGY_202502);
        this.previous = previous;
    }

    @Override
    public void upgradeTopologyGraph(Transaction tx, int fromVersion) throws Exception {
        if (fromVersion < version) {
            previous.upgradeTopologyGraph(tx, fromVersion);
            this.setVersionProperty(tx, version);
            this.addDisplayPropToDatabaseName(tx);
            this.addDefaultLanguageToDatabase(tx);
        }
    }

    private void addDisplayPropToDatabaseName(Transaction tx) {
        tx.findNodes(DATABASE_NAME_LABEL).stream()
                .filter(node -> node.getProperty(QUOTED_DISPLAY_NAME_PROPERTY, null) == null)
                .forEach(node -> {
                    var name = (String) node.getProperty(DATABASE_NAME_PROPERTY);
                    var namespace = (String) node.getProperty(NAMESPACE_PROPERTY, DEFAULT_NAMESPACE);
                    String backtickedName = Stringifier.backtick(name);
                    var displayName = namespace.equals(DEFAULT_NAMESPACE)
                            ? backtickedName
                            : Stringifier.backtick(namespace) + "." + backtickedName;
                    node.setProperty(QUOTED_DISPLAY_NAME_PROPERTY, displayName);
                });
    }

    private void addDefaultLanguageToDatabase(Transaction tx) {
        tx.findNodes(DATABASE_LABEL).stream()
                .filter(node -> node.getProperty(DATABASE_DEFAULT_LANGUAGE_PROPERTY, null) == null)
                .forEach(node ->
                        node.setProperty(DATABASE_DEFAULT_LANGUAGE_PROPERTY, CypherVersion.Cypher5.persistedValue));
    }
}
