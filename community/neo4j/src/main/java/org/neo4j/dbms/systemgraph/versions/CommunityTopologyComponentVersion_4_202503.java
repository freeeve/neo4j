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

import static org.neo4j.dbms.systemgraph.CommunityTopologyGraphVersion.COMMUNITY_TOPOLOGY_202503;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_DEFAULT_LANGUAGE_PROPERTY;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DEFAULT_NAMESPACE;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.NAMESPACE_PROPERTY;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.REMOTE_DATABASE_LABEL;

import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.graphdb.Transaction;

/**
 * This is the CommunityTopologyComponent version for Neo4j 2025.03
 */
public class CommunityTopologyComponentVersion_4_202503 extends KnownCommunityTopologyComponentVersion {

    private final KnownCommunityTopologyComponentVersion previous;

    public CommunityTopologyComponentVersion_4_202503(KnownCommunityTopologyComponentVersion previous) {
        super(COMMUNITY_TOPOLOGY_202503);
        this.previous = previous;
    }

    @Override
    public void upgradeTopologyGraph(Transaction tx, int fromVersion) throws Exception {
        if (fromVersion < version) {
            previous.upgradeTopologyGraph(tx, fromVersion);
            this.setVersionProperty(tx, version);
            this.addDefaultLanguageToAlias(tx);
        }
    }

    private void addDefaultLanguageToAlias(Transaction tx) {
        tx.findNodes(REMOTE_DATABASE_LABEL).stream()
                // constituent aliases should not get a default language
                .filter(node -> node.getProperty(NAMESPACE_PROPERTY, DEFAULT_NAMESPACE) == DEFAULT_NAMESPACE)
                .filter(node -> node.getProperty(DATABASE_DEFAULT_LANGUAGE_PROPERTY, null) == null)
                .forEach(node ->
                        node.setProperty(DATABASE_DEFAULT_LANGUAGE_PROPERTY, CypherVersion.Cypher5.persistedValue));
    }
}
