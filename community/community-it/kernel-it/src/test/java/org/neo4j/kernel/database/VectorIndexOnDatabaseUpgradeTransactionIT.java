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
package org.neo4j.kernel.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.neo4j.gqlstatus.ErrorGqlStatusObjectAssertions.assertThatThrownBy;
import static org.neo4j.test.UpgradeTestUtil.assertKernelVersion;
import static org.neo4j.test.UpgradeTestUtil.upgradeDbms;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.common.EntityType;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.database.DbmsRuntimeVersion;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.exceptions.KernelException;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.graphdb.schema.IndexSettingUtil;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.internal.schema.SchemaDescriptors;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.ZippedStore;
import org.neo4j.kernel.ZippedStoreCommunity;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexVersion;
import org.neo4j.kernel.api.schema.vector.VectorTestUtils;
import org.neo4j.kernel.api.schema.vector.VectorTestUtils.VectorIndexSettings;
import org.neo4j.kernel.impl.coreapi.TransactionImpl;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.LatestVersions;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.Tokens;
import org.neo4j.test.UpgradeTestUtil;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class VectorIndexOnDatabaseUpgradeTransactionIT {
    // keep these for future testing purposes
    private static final KernelVersion KERNEL_VERSION = LatestVersions.LATEST_KERNEL_VERSION;
    private static final DbmsRuntimeVersion RUNTIME_VERSION = DbmsRuntimeVersion.fromKernelVersion(KERNEL_VERSION);
    private static final Tokens.Suppliers.Label LABELS = Tokens.Suppliers.UUID.LABEL;
    private static final Tokens.Suppliers.RelationshipType REL_TYPES = Tokens.Suppliers.UUID.RELATIONSHIP_TYPE;
    private static final Tokens.Suppliers.PropertyKey PROP_KEYS = Tokens.Suppliers.UUID.PROPERTY_KEY;
    private static final Tokens.Factories.Label LABEL_IDS = Tokens.Factories.LABEL;
    private static final Tokens.Factories.RelationshipType REL_TYPE_IDS = Tokens.Factories.RELATIONSHIP_TYPE;
    private static final Tokens.Factories.PropertyKey PROP_KEY_IDS = Tokens.Factories.PROPERTY_KEY;

    @Inject
    private TestDirectory testDirectory;

    private DatabaseManagementService dbms;
    private GraphDatabaseAPI database;

    @AfterEach
    void tearDown() {
        if (dbms != null) {
            dbms.shutdown();
        }
    }

    @ParameterizedTest
    @MethodSource("indexVersions")
    void shouldBeBlockedFromCreatingVectorIndexOnOlderVersion(EntityType entityType, VectorIndexVersion indexVersion) {
        final KernelVersion previousVersion = previousFrom(indexVersion.minimumRequiredKernelVersion());
        setup(previousVersion);

        final KernelVersion minimumRequiredVersion =
                switch (entityType) {
                    case NODE -> indexVersion.minimumRequiredKernelVersion();
                    case RELATIONSHIP ->
                        KernelVersion.getForVersion((byte) Math.max(
                                KernelVersion.VERSION_VECTOR_2_INTRODUCED.version(),
                                indexVersion.minimumRequiredKernelVersion().version()));
                };

        assertThatThrownBy(() -> {
                    try (final Transaction tx = database.beginTx()) {
                        createIndex(tx, entityType, indexVersion, defaultSettings());
                        tx.commit();
                    }
                })
                .isInstanceOf(InvalidArgumentException.class)
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_51N31)
                .hasMessageContainingAll(
                        "Creating a",
                        entityType.name().toLowerCase(),
                        "vector index with provider",
                        indexVersion.descriptor().name(),
                        "is not supported in",
                        previousVersion.name(),
                        "Required version for operation is",
                        minimumRequiredVersion.name(),
                        "Please upgrade DBMS");
    }

    @ParameterizedTest
    @MethodSource("indexVersions")
    void shouldBePossibleToCreateVectorIndexAfterUpgrade(EntityType entityType, VectorIndexVersion indexVersion) {
        final KernelVersion previousVersion = previousFrom(indexVersion.minimumRequiredKernelVersion());
        setup(previousVersion);
        UpgradeTestUtil.upgradeDatabase(dbms, database, previousVersion, KERNEL_VERSION);

        try (final Transaction tx = database.beginTx()) {
            createIndex(tx, entityType, indexVersion, defaultSettings());
            tx.commit();
        }

        try (final Transaction tx = database.beginTx()) {
            assertThat(getVectorIndexes(tx)).hasSize(1);
        }
    }

    @ParameterizedTest
    @MethodSource("indexVersions")
    void createVectorIndexShouldTriggerUpgrade(EntityType entityType, VectorIndexVersion indexVersion) {
        final KernelVersion previousVersion = previousFrom(indexVersion.minimumRequiredKernelVersion());
        setup(previousVersion);
        // No exception should be thrown since we expect the upgrade of version to happen before applying the
        // create transaction.
        upgradeDbms(dbms);
        assertKernelVersion(database, previousVersion);
        try (final Transaction tx = database.beginTx()) {
            createIndex(tx, entityType, indexVersion, defaultSettings());
            tx.commit();
        }

        assertKernelVersion(database, KERNEL_VERSION);
        try (final Transaction tx = database.beginTx()) {
            assertThat(getVectorIndexes(tx)).hasSize(1);
        }
    }

    private static Stream<Arguments> indexVersions() {
        return Stream.of(
                Arguments.of(EntityType.NODE, VectorIndexVersion.V1_0),
                Arguments.of(EntityType.NODE, VectorIndexVersion.V2_0),
                Arguments.of(EntityType.NODE, VectorIndexVersion.V3_0),
                Arguments.of(EntityType.RELATIONSHIP, VectorIndexVersion.V2_0),
                Arguments.of(EntityType.RELATIONSHIP, VectorIndexVersion.V3_0));
    }

    @ParameterizedTest
    @MethodSource("introducedSettings")
    void shouldBeBlockedFromCreatingVectorIndexWithNewSettingsOnOlderVersion(
            VectorIndexVersion indexVersion, EntityType entityType, IndexSetting setting, Object validValue) {
        final KernelVersion introducedKernelVersion =
                VectorIndexConfigUtils.INDEX_SETTING_INTRODUCED_VERSIONS.get(setting);
        final KernelVersion previousVersion = previousFrom(introducedKernelVersion);
        setup(previousVersion);
        assertThatThrownBy(() -> {
                    try (final Transaction tx = database.beginTx()) {
                        createIndex(
                                tx, entityType, indexVersion, defaultSettings().set(setting, validValue));
                        tx.commit();
                    }
                })
                .isInstanceOf(InvalidArgumentException.class)
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_51N31)
                .hasMessageContainingAll(
                        "Creating a vector index with provided settings is not supported in",
                        previousVersion.name(),
                        "Required version for operation is",
                        introducedKernelVersion.name(),
                        "Please upgrade DBMS");
    }

    @ParameterizedTest
    @MethodSource("introducedSettings")
    void shouldBePossibleToCreateVectorIndexWithNewSettingsAfterUpgrade(
            VectorIndexVersion indexVersion, EntityType entityType, IndexSetting setting, Object validValue) {
        final KernelVersion introducedKernelVersion =
                VectorIndexConfigUtils.INDEX_SETTING_INTRODUCED_VERSIONS.get(setting);
        final KernelVersion previousVersion = previousFrom(introducedKernelVersion);
        setup(previousVersion);
        UpgradeTestUtil.upgradeDatabase(dbms, database, previousVersion, KERNEL_VERSION);

        try (final Transaction tx = database.beginTx()) {
            createIndex(tx, entityType, indexVersion, defaultSettings().set(setting, validValue));
            tx.commit();
        }

        try (final Transaction tx = database.beginTx()) {
            assertThat(tx.schema().getIndexes()).hasSize(1);
        }
    }

    private static Stream<Arguments> introducedSettings() {
        final List<Pair<IndexSetting, Object>> introducedSettings = List.of(
                Tuples.pair(IndexSetting.vector_Quantization_Enabled(), true),
                Tuples.pair(IndexSetting.vector_Hnsw_M(), 32),
                Tuples.pair(IndexSetting.vector_Hnsw_M(), 256));

        final Builder<Arguments> arguments = Stream.builder();
        for (final Pair<IndexSetting, Object> introducedSetting : introducedSettings) {
            final IndexSetting setting = introducedSetting.getOne();
            final Object validValue = introducedSetting.getTwo();

            final KernelVersion introducedKernelVersion =
                    VectorIndexConfigUtils.INDEX_SETTING_INTRODUCED_VERSIONS.get(setting);
            final VectorIndexVersion version = VectorIndexVersion.latestSupportedVersion(introducedKernelVersion);
            for (final EntityType entityType : EntityType.values()) {
                arguments.accept(Arguments.of(version, entityType, setting, validValue));
            }
        }
        return arguments.build();
    }

    @ParameterizedTest
    @MethodSource("multiTokenIndexVersions")
    void shouldBeBlockedFromCreatingVectorIndexWithMultiToken(VectorIndexVersion indexVersion, EntityType entityType) {
        final KernelVersion previousVersion = previousFrom(KernelVersion.VERSION_VECTOR_INDEX_SINGLE_STAGE_FILTERING);
        setup(previousVersion);
        assertThatThrownBy(() -> {
                    try (final Transaction tx = database.beginTx()) {
                        createIndex(tx, entityType, 2, 2, indexVersion, defaultSettings());
                        tx.commit();
                    }
                })
                .isInstanceOf(InvalidArgumentException.class)
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_51N31)
                .hasMessageContainingAll(
                        "Creating a",
                        "vector index is not supported in",
                        previousVersion.name(),
                        "Required version for operation is",
                        KernelVersion.VERSION_VECTOR_INDEX_SINGLE_STAGE_FILTERING.name(),
                        "Please upgrade DBMS");
    }

    @ParameterizedTest
    @MethodSource("multiTokenIndexVersions")
    void shouldBePossibleToCreateVectorIndexWithMultiTokenAfterUpgrade(
            VectorIndexVersion indexVersion, EntityType entityType) {
        final KernelVersion previousVersion = previousFrom(KernelVersion.VERSION_VECTOR_INDEX_SINGLE_STAGE_FILTERING);
        setup(previousVersion);
        UpgradeTestUtil.upgradeDatabase(dbms, database, previousVersion, KERNEL_VERSION);

        try (final Transaction tx = database.beginTx()) {
            createIndex(tx, entityType, 2, 2, indexVersion, defaultSettings());
            tx.commit();
        }

        try (final Transaction tx = database.beginTx()) {
            assertThat(tx.schema().getIndexes()).hasSize(1);
        }
    }

    private static Stream<Arguments> multiTokenIndexVersions() {
        final Builder<Arguments> arguments = Stream.builder();
        final VectorIndexVersion minimumVersion =
                VectorIndexVersion.latestSupportedVersion(KernelVersion.VERSION_VECTOR_INDEX_SINGLE_STAGE_FILTERING);
        for (final VectorIndexVersion version : VectorTestUtils.inclusiveVersionRangeFrom(minimumVersion)) {
            for (final EntityType entityType : EntityType.values()) {
                arguments.accept(Arguments.of(version, entityType));
            }
        }
        return arguments.build();
    }

    private void createIndex(
            Transaction tx, EntityType entityType, VectorIndexVersion indexVersion, VectorIndexSettings settings) {
        createIndex(tx, entityType, 1, 1, indexVersion, settings);
    }

    private void createIndex(
            Transaction tx,
            EntityType entityType,
            int numberOfEntityTokens,
            int numberOfPropertyKeys,
            VectorIndexVersion indexVersion,
            VectorIndexSettings settings) {
        try {
            final KernelTransaction ktx = ((TransactionImpl) tx).kernelTransaction();
            final int[] propKeyIds = PROP_KEY_IDS.getIds(ktx, PROP_KEYS.get(numberOfPropertyKeys));
            final int[] entityTokenIds =
                    switch (entityType) {
                        case NODE -> LABEL_IDS.getIds(ktx, LABELS.get(numberOfEntityTokens));
                        case RELATIONSHIP -> REL_TYPE_IDS.getIds(ktx, REL_TYPES.get(numberOfEntityTokens));
                    };
            final SchemaDescriptor schemaDescriptor =
                    switch (indexVersion) {
                        case V1_0, V2_0 ->
                            switch (entityType) {
                                case NODE -> SchemaDescriptors.forLabel(entityTokenIds[0], propKeyIds[0]);
                                case RELATIONSHIP -> SchemaDescriptors.forRelType(entityTokenIds[0], propKeyIds[0]);
                            };

                        case V3_0 -> SchemaDescriptors.forSemanticSearch(entityType, entityTokenIds, propKeyIds);

                        case UNKNOWN -> throw new IllegalArgumentException("Unknown vector index version");
                    };

            final IndexPrototype prototype = IndexPrototype.forSchema(schemaDescriptor)
                    .withIndexType(IndexType.VECTOR)
                    .withIndexProvider(indexVersion.descriptor())
                    .withIndexConfig(settings.toIndexConfig());
            ktx.schemaWrite().indexCreate(prototype);
        } catch (KernelException exception) {
            throw new RuntimeException(exception);
        }
    }

    private VectorIndexSettings defaultSettings() {
        return VectorIndexSettings.from(IndexSettingUtil.defaultSettingsForTesting(IndexType.VECTOR.toPublicApi()));
    }

    private KernelVersion previousFrom(KernelVersion kernelVersion) {
        if (kernelVersion == KernelVersion.GLORIOUS_FUTURE) {
            kernelVersion = LatestVersions.LATEST_KERNEL_VERSION;
        }
        return KernelVersion.precedingVersion(kernelVersion);
    }

    private static List<IndexDefinition> getVectorIndexes(Transaction tx) {
        return Iterables.stream(tx.schema().getIndexes())
                .filter(id -> id.getIndexType() == IndexType.VECTOR.toPublicApi())
                .toList();
    }

    private void setup(KernelVersion kernelVersion) {
        final ZippedStoreCommunity store =
                switch (kernelVersion) {
                    case V5_10 -> ZippedStoreCommunity.REC_AF11_V510_EMPTY;
                    case V5_15 -> ZippedStoreCommunity.REC_AF11_V515_EMPTY;
                    case V5_22 -> ZippedStoreCommunity.REC_AF11_V522_EMPTY;
                    case V2025_08 -> ZippedStoreCommunity.REC_AF11_V5202508_EMPTY;
                    case V2025_11 -> ZippedStoreCommunity.REC_AF11_V202511_EMPTY;
                    default ->
                        throw InvalidArgumentException.internalError(
                                this.getClass().getSimpleName(),
                                "Test not setup to find a %s for %s."
                                        .formatted(ZippedStore.class.getSimpleName(), kernelVersion));
                };
        setup(store);
    }

    private void setup(ZippedStoreCommunity snapshot) {
        try {
            snapshot.unzip(testDirectory.homePath());
        } catch (IOException exc) {
            fail("Could not setup %s:%s".formatted(snapshot.name(), exc));
        }
        dbms = new TestDatabaseManagementServiceBuilder(testDirectory.homePath())
                .setConfig(GraphDatabaseInternalSettings.automatic_upgrade_enabled, false)
                .setConfig(GraphDatabaseInternalSettings.latest_runtime_version, RUNTIME_VERSION.getVersion())
                .setConfig(GraphDatabaseInternalSettings.latest_kernel_version, KERNEL_VERSION.version())
                .setConfig(GraphDatabaseInternalSettings.always_use_latest_index_provider, false)
                .setConfig(GraphDatabaseInternalSettings.vector_single_stage_filtering_enabled, true)
                .build();
        database = (GraphDatabaseAPI) dbms.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        assertKernelVersion(database, snapshot.statistics().kernelVersion());
    }
}
