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
package org.neo4j.cypher.internal.compiler.planner.logical.schema

import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class ImpliedLabelsMapTest extends CypherFunSuite {

  test("should be empty for empty input") {
    val ilm = ImpliedLabelsMap.forLabels(Set.empty, Map.empty)
    ilm.labelToImpliedLabels shouldBe empty
  }

  test("should be empty with no implied labels") {
    val ilm = ImpliedLabelsMap.forLabels(Set("A", "B"), _ => Set.empty)
    ilm.labelToImpliedLabels shouldBe empty
  }

  test("should be empty with no relevant implied labels") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("A", "B"),
      Map("X" -> Set("Y")).withDefaultValue(Set.empty)
    )
    ilm.labelToImpliedLabels shouldBe empty
  }

  test("should contain implied labels, one hop") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("Actor", "Cat"),
      Map(
        "Actor" -> Set("Person"),
        "Cat" -> Set("Animal")
      ).withDefaultValue(Set.empty)
    )

    ilm.labelToImpliedLabels shouldBe Map(
      "Actor" -> Set("Person"),
      "Cat" -> Set("Animal")
    )
  }

  test("should prune implied labels, one hop") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("Actor", "Cat"),
      Map(
        "Actor" -> Set("Person"),
        "Cat" -> Set("Animal")
      ).withDefaultValue(Set.empty)
    )

    ilm.pruneImpliedLabels(Set("Actor", "Person")) shouldBe Set("Actor")
    ilm.pruneImpliedLabels(Set("Cat", "Animal")) shouldBe Set("Cat")
    ilm.pruneImpliedLabels(Set("Actor", "Person", "Cat", "Animal", "Other")) shouldBe Set("Actor", "Cat", "Other")
  }

  test("should contain implied labels, multiple hops") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("Actor", "Person"),
      Map(
        "Actor" -> Set("Person"),
        "Person" -> Set("Entity"),
        "Entity" -> Set("Object")
      ).withDefaultValue(Set.empty)
    )

    ilm.labelToImpliedLabels shouldBe Map(
      "Actor" -> Set("Person", "Entity", "Object"),
      "Person" -> Set("Entity", "Object")
    )
  }

  test("should prune implied labels, multiple hops") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("Actor", "Person", "Entity", "Object"),
      Map(
        "Actor" -> Set("Person"),
        "Person" -> Set("Entity"),
        "Entity" -> Set("Object")
      ).withDefaultValue(Set.empty)
    )

    ilm.pruneImpliedLabels(Set("Actor", "Entity")) shouldBe Set("Actor")
    ilm.pruneImpliedLabels(Set("Person", "Object")) shouldBe Set("Person")
    ilm.pruneImpliedLabels(Set("Actor", "Person", "Entity", "Object", "Other")) shouldBe Set("Actor", "Other")
  }

  test("should contain implied labels, multiple hops with branching") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("A", "B"),
      Map(
        "A" -> Set("X", "AA"),
        "B" -> Set("X", "BB"),
        "X" -> Set("Y", "Z")
      ).withDefaultValue(Set.empty)
    )

    ilm.labelToImpliedLabels shouldBe Map(
      "A" -> Set("X", "AA", "Y", "Z"),
      "B" -> Set("X", "BB", "Y", "Z")
    )
  }

  test("should prune implied labels, multiple hops with branching") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("A", "B"),
      Map(
        "A" -> Set("X", "AA"),
        "B" -> Set("X", "BB"),
        "X" -> Set("Y", "Z")
      ).withDefaultValue(Set.empty)
    )

    ilm.pruneImpliedLabels(Set("A", "AA", "Y")) shouldBe Set("A")
    ilm.pruneImpliedLabels(Set("B", "BB", "Z")) shouldBe Set("B")
    ilm.pruneImpliedLabels(Set("A", "B", "Y", "Z")) shouldBe Set("A", "B")
  }

  test("should contain self-references") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("A", "B"),
      Map(
        "A" -> Set("B"),
        "B" -> Set("C"),
        "C" -> Set("A")
      ).withDefaultValue(Set.empty)
    )

    ilm.labelToImpliedLabels shouldBe Map(
      "A" -> Set("A", "B", "C"),
      "B" -> Set("A", "B", "C")
    )
  }

  test("should not prune all labels in case of self-references") {
    val ilm = ImpliedLabelsMap.forLabels(
      Set("X", "B"),
      Map(
        "X" -> Set("B"),
        "B" -> Set("C"),
        "C" -> Set("X")
      ).withDefaultValue(Set.empty)
    )

    val xbc = Set("X", "B", "C")

    ilm.pruneImpliedLabels(Set("X", "B", "C")) should (
      have size 1 and contain oneElementOf xbc
    )
    ilm.pruneImpliedLabels(Set("X", "B", "C", "Other")) should (
      have size 2 and contain("Other") and contain oneElementOf xbc
    )
  }

  test("should not infer implied labels past the iteration limit") {
    // A0 -> A1 -> ... -> A{LIMIT + 100}
    val manyImplications: Map[String, Set[String]] =
      Range.inclusive(0, ImpliedLabelsMap.INFERENCE_ITERATION_LIMIT + 100)
        .map(i => s"A$i" -> Set(s"A${i + 1}"))
        .toMap
        .withDefaultValue(Set.empty)

    val ilm = ImpliedLabelsMap.forLabels(Set("A0"), manyImplications)

    // A1 -> A2 -> ... -> A{LIMIT}
    val expected =
      Range.inclusive(1, ImpliedLabelsMap.INFERENCE_ITERATION_LIMIT)
        .map(i => s"A$i")
        .toSet

    ilm.labelToImpliedLabels shouldBe Map("A0" -> expected)
  }
}
