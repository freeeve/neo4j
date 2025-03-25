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
package org.neo4j.dbms.systemgraph;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DEFAULT_NAMESPACE;
import static org.neo4j.kernel.database.DatabaseReferenceImpl.SPD.shardName;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.configuration.helpers.RemoteUri;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.database.DatabaseReference;
import org.neo4j.kernel.database.DatabaseReferenceImpl;
import org.neo4j.kernel.database.NormalizedCatalogEntry;
import org.neo4j.kernel.database.NormalizedDatabaseName;

public class CommunityTopologyGraphDbmsModelIT extends BaseTopologyGraphDbmsModelIT {
    private CommunityTopologyGraphDbmsModel dbmsModel;

    @Override
    protected void createModel(Transaction tx) {
        dbmsModel = new CommunityTopologyGraphDbmsModel(tx);
    }

    protected TopologyGraphDbmsModel dbmsModel() {
        return dbmsModel;
    }

    private static NormalizedDatabaseName name(String name) {
        return new NormalizedDatabaseName(name);
    }

    @Test
    void canReturnAllCompositeDatabaseReferences() {
        // given
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var remAliasId1 = randomUUID();
        var remAliasId2 = randomUUID();
        var remAliasId3 = randomUUID();
        var remAliasId4 = randomUUID();
        var remAliasId5 = randomUUID();
        var locDb = newDatabase(b -> b.withDatabase("loc"));
        createInternalReferenceForDatabase(tx, "locAlias", false, locDb);
        createExternalReferenceForDatabase(tx, "remAlias", "rem1", remoteNeo4j, remAliasId1);
        var compDb1 = newDatabase(b -> b.withDatabase("compDb1").asVirtual());
        var compDb1Name = compDb1.normalizedName();
        createInternalReferenceForDatabase(tx, compDb1.name(), true, compDb1);
        createInternalReferenceForDatabase(tx, compDb1.name(), "locAlias", false, locDb);
        createExternalReferenceForDatabase(tx, compDb1.name(), "remAlias", "rem2", remoteNeo4j, remAliasId2);
        createExternalReferenceForDatabase(tx, compDb1.name(), "remAlias2", "rem3", remoteNeo4j, remAliasId3);
        var compDb2 = newDatabase(b -> b.withDatabase("compDb2").asVirtual());
        var compDb2Name = compDb2.normalizedName();
        createInternalReferenceForDatabase(tx, compDb2.name(), true, compDb2);
        createInternalReferenceForDatabase(tx, compDb2.name(), "locAlias", false, locDb);
        createExternalReferenceForDatabase(tx, compDb2.name(), "remAlias", "rem4", remoteNeo4j, remAliasId4);
        createExternalReferenceForDatabase(tx, compDb2.name(), "remAlias3", "rem5", remoteNeo4j, remAliasId5);

        // then
        var comp1Ref = new DatabaseReferenceImpl.Composite(
                compDb1Name,
                compDb1,
                Set.of(
                        new DatabaseReferenceImpl.Internal(name("locAlias"), compDb1Name, locDb, false),
                        new DatabaseReferenceImpl.External(
                                name("rem2"), name("remAlias"), compDb1Name, remoteNeo4j, remAliasId2),
                        new DatabaseReferenceImpl.External(
                                name("rem3"), name("remAlias2"), compDb1Name, remoteNeo4j, remAliasId3)));
        var comp2Ref = new DatabaseReferenceImpl.Composite(
                compDb2Name,
                compDb2,
                Set.of(
                        new DatabaseReferenceImpl.Internal(name("locAlias"), compDb2Name, locDb, false),
                        new DatabaseReferenceImpl.External(
                                name("rem4"), name("remAlias"), compDb2Name, remoteNeo4j, remAliasId4),
                        new DatabaseReferenceImpl.External(
                                name("rem5"), name("remAlias3"), compDb2Name, remoteNeo4j, remAliasId5)));

        assertThat(dbmsModel().getAllCompositeDatabaseReferences()).isEqualTo(Set.of(comp1Ref, comp2Ref));

        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(compDb1.name())))
                .hasValue(comp1Ref);
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(compDb2.name())))
                .hasValue(comp2Ref);
        // since no reference was explicitly created this is empty - this in artefact of the test setup
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(locDb.name())))
                .isEmpty();
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry("locAlias")))
                .hasValue(new DatabaseReferenceImpl.Internal(name("locAlias"), locDb, false));
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry("remAlias")))
                .hasValue(new DatabaseReferenceImpl.External(name("rem1"), name("remAlias"), remoteNeo4j, remAliasId1));
    }

    @Test
    void canReturnAllShardedPropertyDatabaseReferences() {
        // given
        var foo0 = newDatabase(b -> b.withDatabase(shardName("foo", 0)));
        var foo1 = newDatabase(b -> b.withDatabase(shardName("foo", 1)));
        var foo = newDatabase(b -> b.withDatabase("foo").withShards(foo0, foo1));
        createInternalReferenceForDatabase(tx, foo0.name(), true, foo0);
        createInternalReferenceForDatabase(tx, foo1.name(), true, foo1);
        createInternalReferenceForDatabase(tx, foo.name(), true, foo);

        var bar0 = newDatabase(b -> b.withDatabase(shardName("bar", 0)));
        var bar1 = newDatabase(b -> b.withDatabase(shardName("bar", 1)));
        var bar2 = newDatabase(b -> b.withDatabase(shardName("bar", 2)));
        var bar3 = newDatabase(b -> b.withDatabase(shardName("bar", 3)));
        var bar = newDatabase(b -> b.withDatabase("bar").withShards(bar0, bar1, bar2, bar3));
        createInternalReferenceForDatabase(tx, bar0.name(), true, bar0);
        createInternalReferenceForDatabase(tx, bar1.name(), true, bar1);
        createInternalReferenceForDatabase(tx, bar2.name(), true, bar2);
        createInternalReferenceForDatabase(tx, bar3.name(), true, bar3);
        createInternalReferenceForDatabase(tx, bar.name(), true, bar);

        // then
        var foo0Ref = new DatabaseReferenceImpl.SPDShard(foo0.normalizedName(), foo0, true, foo.name());
        var foo1Ref = new DatabaseReferenceImpl.SPDShard(foo1.normalizedName(), foo1, true, foo.name());
        var fooShards = Map.<Integer, DatabaseReference>of(
                0, foo0Ref,
                1, foo1Ref);
        var fooRef = new DatabaseReferenceImpl.SPD(foo.normalizedName(), foo, fooShards);
        var bar0Ref = new DatabaseReferenceImpl.SPDShard(bar0.normalizedName(), bar0, true, bar.name());
        var bar1Ref = new DatabaseReferenceImpl.SPDShard(bar1.normalizedName(), bar1, true, bar.name());
        var bar2Ref = new DatabaseReferenceImpl.SPDShard(bar2.normalizedName(), bar2, true, bar.name());
        var bar3Ref = new DatabaseReferenceImpl.SPDShard(bar3.normalizedName(), bar3, true, bar.name());
        var barShards = Map.<Integer, DatabaseReference>of(
                0, bar0Ref,
                1, bar1Ref,
                2, bar2Ref,
                3, bar3Ref);
        var barRef = new DatabaseReferenceImpl.SPD(bar.normalizedName(), bar, barShards);

        assertThat(dbmsModel().getAllDatabaseReferences())
                .containsExactlyInAnyOrder(fooRef, foo0Ref, foo1Ref, barRef, bar0Ref, bar1Ref, bar2Ref, bar3Ref);
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(foo.name())))
                .hasValue(fooRef);
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(bar.name())))
                .hasValue(barRef);
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(foo0.name())))
                .hasValue(fooRef.entityDetailStores().get(0));
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(foo1.name())))
                .hasValue(fooRef.entityDetailStores().get(1));
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(bar0.name())))
                .hasValue(barRef.entityDetailStores().get(0));
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(bar1.name())))
                .hasValue(barRef.entityDetailStores().get(1));
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(bar2.name())))
                .hasValue(barRef.entityDetailStores().get(2));
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(bar3.name())))
                .hasValue(barRef.entityDetailStores().get(3));
    }

    @Test
    void canReturnAllMirrorDatabaseReferences() {
        // given
        var upstream = newDatabase(b -> b.withDatabase("upstream"));
        var mirror0 = newDatabase(b -> b.withDatabase("mirror0").withUpstream(upstream));
        var mirror1 = newDatabase(b -> b.withDatabase("mirror1").withUpstream(upstream));
        createInternalReferenceForDatabase(tx, mirror0.name(), true, mirror0);
        createInternalReferenceForDatabase(tx, mirror1.name(), true, mirror1);
        createInternalReferenceForDatabase(tx, upstream.name(), true, upstream);

        // then
        var mirror0Ref = new DatabaseReferenceImpl.Mirror(mirror0.normalizedName(), mirror0, upstream.name());
        var mirror1Ref = new DatabaseReferenceImpl.Mirror(mirror1.normalizedName(), mirror1, upstream.name());
        var upstreamRef = new DatabaseReferenceImpl.Internal(upstream.normalizedName(), upstream, true);

        assertThat(dbmsModel().getAllDatabaseReferences())
                .containsExactlyInAnyOrder(mirror0Ref, mirror1Ref, upstreamRef);
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(mirror0.name())))
                .hasValue(mirror0Ref);
        assertThat(dbmsModel().getDatabaseRefByAlias(new NormalizedCatalogEntry(mirror1.name())))
                .hasValue(mirror1Ref);
    }

    @Test
    void returnsBestReference() {
        var real0Db = newDatabase(b -> b.withDatabase("real0"));
        var real1Db = newDatabase(b -> b.withDatabase("real1"));
        var real2Db = newDatabase(b -> b.withDatabase("real2"));
        var real3Db = newDatabase(b -> b.withDatabase("real3"));

        var comp00Db = newDatabase(b -> b.withDatabase("golf.hotel.india").asVirtual());
        createInternalReferenceForDatabase(tx, comp00Db.name(), true, comp00Db);
        createInternalReferenceForDatabase(tx, comp00Db.name(), "juliet", false, real0Db);

        assertThat(dbmsModel().getDatabaseRefByAlias("golf.hotel.india.juliet"))
                .map(DatabaseReference::namedDatabaseId)
                .hasValue(real0Db);

        var comp01Db = newDatabase(b -> b.withDatabase("golf.hotel").asVirtual());
        createInternalReferenceForDatabase(tx, comp01Db.name(), true, comp01Db);
        createInternalReferenceForDatabase(tx, comp01Db.name(), "india.juliet", false, real1Db);

        assertThat(dbmsModel().getDatabaseRefByAlias("golf.hotel.india.juliet"))
                .map(DatabaseReference::namedDatabaseId)
                .hasValue(real1Db);

        var comp02Db = newDatabase(b -> b.withDatabase("golf").asVirtual());
        createInternalReferenceForDatabase(tx, comp02Db.name(), true, comp02Db);
        createInternalReferenceForDatabase(tx, comp02Db.name(), "hotel.india.juliet", false, real2Db);

        assertThat(dbmsModel().getDatabaseRefByAlias("golf.hotel.india.juliet"))
                .map(DatabaseReference::namedDatabaseId)
                .hasValue(real2Db);

        createInternalReferenceForDatabase(tx, "golf.hotel.india.juliet", false, real3Db);

        assertThat(dbmsModel().getDatabaseRefByAlias("golf.hotel.india.juliet"))
                .map(DatabaseReference::namedDatabaseId)
                .hasValue(real3Db);
    }

    @Test
    void canReturnAllDatabaseReferences() {
        // given
        var fooDb = newDatabase(b -> b.withDatabase("foo"));
        createInternalReferenceForDatabase(tx, "fooAlias", false, fooDb);
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var barId = UUID.randomUUID();
        var barId2 = UUID.randomUUID();
        createExternalReferenceForDatabase(tx, "bar", "foo", remoteNeo4j, barId);
        var compDb1 = newDatabase(b -> b.withDatabase("compDb1").asVirtual());
        var compDb1Name = compDb1.normalizedName();
        createInternalReferenceForDatabase(tx, compDb1.name(), true, compDb1);
        createInternalReferenceForDatabase(tx, compDb1.name(), "locAlias", false, fooDb);
        createExternalReferenceForDatabase(tx, compDb1.name(), "remAlias", "rem", remoteNeo4j, barId2);

        var expected = Set.of(
                new DatabaseReferenceImpl.External(name("foo"), name("bar"), remoteNeo4j, barId),
                new DatabaseReferenceImpl.Internal(name("foo"), fooDb, true),
                new DatabaseReferenceImpl.Internal(name("fooAlias"), fooDb, false),
                new DatabaseReferenceImpl.Composite(
                        compDb1.normalizedName(),
                        compDb1,
                        Set.of(
                                new DatabaseReferenceImpl.Internal(name("locAlias"), compDb1Name, fooDb, false),
                                new DatabaseReferenceImpl.External(
                                        name("rem"), name("remAlias"), compDb1Name, remoteNeo4j, barId2))));

        // when
        var aliases = dbmsModel().getAllDatabaseReferences();

        // then
        assertThat(aliases).isEqualTo(expected);
    }

    @Test
    void shouldReturnEmptyForDriverSettingsIfNoneExist() {
        // given
        var aliasName = "fooAlias";
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        createExternalReferenceForDatabase(tx, aliasName, "foo", remoteNeo4j, randomUUID());

        // when
        var result = dbmsModel().getDriverSettings(aliasName, DEFAULT_NAMESPACE);

        // then
        assertThat(result).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("driverSettings")
    void canReturnDriverSettingsForExternalDatabaseReference(String ignore, DriverSettings driverSettings) {
        // given
        var aliasName = "fooAlias";
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var fooId = randomUUID();
        var aliasNode = createExternalReferenceForDatabase(tx, aliasName, "foo", remoteNeo4j, fooId);

        createDriverSettingsForExternalAlias(tx, aliasNode, driverSettings);

        // when
        var result = dbmsModel().getDriverSettings(aliasName, DEFAULT_NAMESPACE);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(driverSettings);
    }

    @Test
    void shouldReturnExternalDatabaseCredentialsForExternalDatabaseReference() {
        // given
        var aliasName = "fooAlias";
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var fooId = randomUUID();
        createExternalReferenceForDatabase(tx, aliasName, "foo", remoteNeo4j, fooId);

        // when
        var result = dbmsModel()
                .getExternalDatabaseCredentials(
                        new DatabaseReferenceImpl.External(name("foo"), name(aliasName), remoteNeo4j, fooId));

        // then
        assertThat(result).isPresent();
        assertThat(result.map(ExternalDatabaseCredentials::username)).hasValue("username");
        assertThat(result.map(ExternalDatabaseCredentials::password)).hasValue("password".getBytes());
        assertThat(result.map(ExternalDatabaseCredentials::iv)).hasValue("i_vector".getBytes());
    }

    @ParameterizedTest
    @MethodSource("aliasProperties")
    void canReturnPropertiesForExternalDatabaseReference(Map<String, Object> properties) {
        // given
        var aliasName = "fooAlias";
        var aliasNamespace = "composite";
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var fooId = randomUUID();
        var aliasNode = createExternalReferenceForDatabase(tx, aliasNamespace, aliasName, "foo", remoteNeo4j, fooId);

        createPropertiesForAlias(tx, aliasNode, properties);

        // when
        var result = dbmsModel().getAliasProperties(aliasName, aliasNamespace);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(properties);
    }

    @ParameterizedTest
    @MethodSource("aliasProperties")
    void canReturnPropertiesForInternalDatabaseReference(Map<String, Object> properties) {
        // given
        var aliasName = "fooAlias";
        var aliasNamespace = "composite";
        var locDb = newDatabase(b -> b.withDatabase("loc"));
        var aliasNode = createInternalReferenceForDatabase(tx, aliasNamespace, aliasName, false, locDb);

        createPropertiesForAlias(tx, aliasNode, properties);

        // when
        var result = dbmsModel().getAliasProperties(aliasName, aliasNamespace);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(properties);
    }

    @Test
    void canReturnRemoteAliasDefaultLanguage() {
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var remAliasId1 = randomUUID();
        var remAliasId2 = randomUUID();
        createExternalReferenceForDatabase(tx, "remote1", "rem1", remoteNeo4j, remAliasId1);
        createExternalReferenceForDatabase(tx, "remote2", "rem1", remoteNeo4j, remAliasId2, CypherVersion.Cypher25);
        var locDb = newDatabase(b -> b.withDatabase("loc"));
        createInternalReferenceForDatabase(tx, "locAlias", false, locDb);

        assertEquals(Optional.of(CypherVersion.Cypher5), dbmsModel().getRemoteAliasLanguageVersion("remote1"));
        assertEquals(Optional.of(CypherVersion.Cypher25), dbmsModel().getRemoteAliasLanguageVersion("remote2"));

        assertEquals(Optional.empty(), dbmsModel().getRemoteAliasLanguageVersion("nonExisting"));
        assertEquals(Optional.empty(), dbmsModel().getRemoteAliasLanguageVersion("locDb"));
        assertEquals(Optional.empty(), dbmsModel().getRemoteAliasLanguageVersion("locAlias"));
    }
}
