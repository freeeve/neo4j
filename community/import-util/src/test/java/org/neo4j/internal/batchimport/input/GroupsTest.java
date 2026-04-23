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
package org.neo4j.internal.batchimport.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.csv.reader.VectorExtractor;
import org.neo4j.test.Race;

class GroupsTest {
    @Test
    void shouldHandleConcurrentGetOrCreate() throws Throwable {
        // GIVEN
        Groups groups = new Groups();
        Race race = new Race();
        String name = "MyGroup";
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            race.addContestant(() -> {
                Group group = groups.getOrCreate(name);
                assertThat(group.id()).isZero();
            });
        }

        // WHEN
        race.go();

        // THEN
        Group otherGroup = groups.getOrCreate("MyOtherGroup");
        assertThat(otherGroup.id()).isOne();
    }

    @Test
    void shouldSupportMixedGroupModeInGetOrCreate() {
        // given
        Groups groups = new Groups();
        var globalGroup = groups.getOrCreate(null);

        // when
        var otherGroup = groups.getOrCreate("Something");

        // then
        assertThat(otherGroup).isNotEqualTo(globalGroup);
    }

    @Test
    void shouldSupportMixedGroupModeInGetOrCreate2() {
        // given
        Groups groups = new Groups();
        var otherGroup = groups.getOrCreate("Something");

        // when
        var globalGroup = groups.getOrCreate(null);

        // then
        assertThat(globalGroup).isNotEqualTo(otherGroup);
    }

    @Test
    void shouldGetCreatedGroup() {
        // given
        Groups groups = new Groups();
        String name = "Something";
        Group createdGroup = groups.getOrCreate(name);

        // when
        Group gottenGroup = groups.get(name);

        // then
        assertThat(gottenGroup).isEqualTo(createdGroup);
    }

    @Test
    void shouldGetGlobalGroup() {
        // given
        Groups groups = new Groups();
        groups.getOrCreate(null);

        // when
        Group group = groups.get(null);

        // then
        assertThat(group.name()).isNull();
        assertThat(group.descriptiveName()).isEqualTo("global id space");
    }

    @Test
    void shouldFailOnGettingNonExistentGroup() {
        // given
        Groups groups = new Groups();

        // when
        assertThatExceptionOfType(HeaderException.class).isThrownBy(() -> groups.get("Something"));
    }

    @Test
    void globalGroupIdIsNotFixedToZero() {
        final var groups1 = new Groups();
        final var g1_0 = groups1.getOrCreate(null);
        assertThat(g1_0.id()).isZero();
        final var g1_1 = groups1.getOrCreate("foo");
        assertThat(g1_1.id()).isOne();
        assertThat(groups1.get(0)).isEqualTo(g1_0);
        assertThat(groups1.get(null)).isEqualTo(g1_0);
        assertThat(groups1.get(1)).isEqualTo(g1_1);
        assertThat(groups1.get("foo")).isEqualTo(g1_1);

        final var groups2 = new Groups();
        final var g2_0 = groups2.getOrCreate("foo");
        assertThat(g2_0.id()).isZero();
        final var g2_1 = groups2.getOrCreate(null);
        assertThat(g2_1.id()).isOne();
        assertThat(groups2.get(0)).isEqualTo(g2_0);
        assertThat(groups2.get("foo")).isEqualTo(g2_0);
        assertThat(groups2.get(1)).isEqualTo(g2_1);
        assertThat(groups2.get(null)).isEqualTo(g2_1);
    }

    @Test
    void shouldGetOnlyNonGlobalGroup() {
        final var groups = new Groups();
        final var g1_0 = groups.getOrCreate("foo");
        assertThat(groups.get(0)).isEqualTo(g1_0);
    }

    @Test
    void shouldValidateSpecificIdTypesForAGroup() {
        var groupName = "foo";
        var keyWithLongType = "id1";
        var keyWithStringType = "id2";
        var keyWithVectorType = "id3";
        var longType = "long";
        var stringType = "string";

        var groups = new Groups();
        var group = groups.getOrCreate(groupName);

        assertThat(groups.getSpecificIdType(group, 0)).isNull();
        assertThat(groups.getSpecificIdType(group, 1)).isNull();

        assertThatCode(() -> groups.bindIdType(group, keyWithLongType, longType))
                .doesNotThrowAnyException();
        assertThat(groups.getSpecificIdType(group, 1)).isNull();

        assertThatCode(() -> groups.bindIdType(group, keyWithLongType, longType))
                .doesNotThrowAnyException();
        assertThatCode(() -> groups.bindIdType(group, keyWithStringType, stringType))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> groups.bindIdType(group, keyWithLongType, stringType))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll(
                        "Group",
                        groupName,
                        "has a different specific type for column",
                        keyWithLongType,
                        "Was created with",
                        longType,
                        "and later used with",
                        stringType);

        assertThatThrownBy(() -> groups.bindIdType(group, keyWithVectorType, VectorExtractor.COL_NAME))
                .isInstanceOf(HeaderException.class)
                .hasMessage("vector is not allowed as an id-type");
    }
}
