#
# Copyright (c) "Neo4j"
# Neo4j Sweden AB [https://neo4j.com]
#
# This file is part of Neo4j.
#
# Neo4j is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

#encoding: utf-8

Feature: PathSelectorWithParametersAcceptance

  Background:
    Given an empty graph

  Scenario Outline: Find same paths with different noise words (PATH, PATHS, GROUP vs GROUPS) - no predicate
    # ┌─┐   ┌─┐   ┌─┐   ┌─┐
    # │A│──▶│B│──▶│C│──▶│D│
    # └─┘   └─┘   └─┘   └─┘
    #  │       ┌─┐       ▲
    #  └──────▶│X│───────┘
    #          └─┘
    And having executed:
      """
      CREATE (a:A), (b:B), (c:C), (d:D), (x:X),
        (a)-[:R]->(b)-[:R]->(c)-[:R]->(d),
        (a)-[:R]->(x)-[:R]->(d)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
      MATCH p = <pathSelector> (:A)-->+(:D)
      WITH nodes(p) AS n ORDER BY size(n)
      RETURN collect(n) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector           | result                                         |
      | SHORTEST $one          | [[(:A), (:X), (:D)]]                           |
      | SHORTEST $one PATH     | [[(:A), (:X), (:D)]]                           |
      | SHORTEST $one PATHS    | [[(:A), (:X), (:D)]]                           |
      | SHORTEST $two          | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $two PATH     | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $two PATHS    | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $three PATH   | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $three PATHS  | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $three        | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $one GROUP    | [[(:A), (:X), (:D)]]                           |
      | SHORTEST $one GROUPS   | [[(:A), (:X), (:D)]]                           |
      | SHORTEST $two GROUP    | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $two GROUPS   | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $three GROUP  | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |
      | SHORTEST $three GROUPS | [[(:A), (:X), (:D)], [(:A), (:B), (:C), (:D)]] |

  Scenario Outline: Find ANY paths with different noise words (PATH, PATHS) - no predicate
    # ┌─┐   ┌─┐   ┌─┐   ┌─┐
    # │A│──▶│B│──▶│C│──▶│D│
    # └─┘   └─┘   └─┘   └─┘
    #  │       ┌─┐       ▲
    #  └──────▶│X│───────┘
    #          └─┘
    And having executed:
      """
      CREATE (a:A), (b:B), (c:C), (d:D), (x:X),
        (a)-[:R]->(b)-[:R]->(c)-[:R]->(d),
        (a)-[:R]->(x)-[:R]->(d)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
      MATCH <pathSelector> (:A)-->+(:D)
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector      | result |
      | ANY $one          | 1      |
      | ANY $one PATH     | 1      |
      | ANY $one PATHS    | 1      |
      | ANY $two          | 2      |
      | ANY $two PATH     | 2      |
      | ANY $two PATHS    | 2      |
      | ANY $three        | 2      |
      | ANY $three PATH   | 2      |
      | ANY $three PATHS  | 2      |

  Scenario Outline: Find same paths with different noise words (PATH, PATHS, GROUP vs GROUPS) - predicate
      # ┌─┐   ┌─┐   ┌─┐   ┌─┐
      # │A│──▶│B│──▶│C│──▶│D│
      # └─┘   └─┘   └─┘   └─┘
      #  │       ┌─┐       ▲
      #  └──────▶│X│───────┘
      #          └─┘
    And having executed:
      """
      CREATE (a:A), (b:B), (c:C), (d:D), (x:X),
        (a)-[:R]->(b)-[:R]->(c)-[:R]->(d),
        (a)-[:R]->(x)-[:R]->(d)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
      MATCH p = <pathSelector> (:A)(()-->(:!X))+(:D)
      WITH nodes(p) AS n ORDER BY size(n)
      RETURN collect(n) AS result
      """
    Then the result should be, in any order:
      | result                     |
      | [[(:A), (:B), (:C), (:D)]] |
    Examples:
      | pathSelector               |
      | SHORTEST $one              |
      | SHORTEST $one PATH         |
      | SHORTEST $one PATHS        |
      | SHORTEST $two              |
      | SHORTEST $two PATH         |
      | SHORTEST $two PATHS        |
      | SHORTEST $one GROUP        |
      | SHORTEST $one PATH GROUP   |
      | SHORTEST $one PATHS GROUP  |
      | SHORTEST $one GROUPS       |
      | SHORTEST $one PATH GROUPS  |
      | SHORTEST $one PATHS GROUPS |
      | SHORTEST $two GROUP        |
      | SHORTEST $two GROUPS       |
      | ANY $one                   |
      | ANY $one PATH              |
      | ANY $one PATHS             |
      | ANY $two                   |
      | ANY $two PATH              |
      | ANY $two PATHS             |

  Scenario Outline: Find ANY paths with different noise words (PATH, PATHS) - predicate
    # ┌─┐   ┌─┐   ┌─┐   ┌─┐
    # │A│──▶│B│──▶│C│──▶│D│
    # └─┘   └─┘   └─┘   └─┘
    #  │       ┌─┐       ▲
    #  └──────▶│X│───────┘
    #          └─┘
    And having executed:
      """
      CREATE (a:A), (b:B), (c:C), (d:D), (x:X),
        (a)-[:R]->(b)-[:R]->(c)-[:R]->(d),
        (a)-[:R]->(x)-[:R]->(d)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
      MATCH <pathSelector> (:A)(()-->(:!X))+(:D)
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector    | result |
      | ANY $one        | 1      |
      | ANY $one PATH   | 1      |
      | ANY $one PATHS  | 1      |
      | ANY $two        | 1      |
      | ANY $two PATH   | 1      |
      | ANY $two PATHS  | 1      |

  Scenario Outline: Element pattern predicates are applied before path selector
    # ┌─┐   ┌─┐   ┌─┐   ┌─┐
    # │A│──▶│ │──▶│ │──▶│B│
    # └─┘   └─┘   └─┘   └─┘
    #  │       ┌─┐       ▲
    #  └──────▶│ │──X────┘
    #          └─┘
    And having executed:
      """
        CREATE (a:A), (b:B),
          (a)-[:R]->()-[:R]->()-[:R]->(b),
          (a)-[:R]->()-[:X]->(b)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
      MATCH p = <pathSelector> (:A)-[r WHERE r:!X]->+(:B)
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | 1        |
    Examples:
      | pathSelector        |
      | SHORTEST $one       |
      | SHORTEST $two       |
      | SHORTEST $one GROUP |
      | SHORTEST $two GROUP |
      | ANY $one            |
      | ANY $two            |

  Scenario Outline: Path pattern predicates are applied before path selector
    # ┌─┐   ┌─┐   ┌─┐   ┌─┐
    # │A│──▶│ │──▶│ │──▶│B│
    # └─┘   └─┘   └─┘   └─┘
    #  │       ┌─┐       ▲
    #  └──────▶│ │──X────┘
    #          └─┘
    And having executed:
      """
        CREATE (a:A), (b:B),
          (a)-[:R]->()-[:R]->()-[:R]->(b),
          (a)-[:R]->()-[:X]->(b)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
      MATCH <pathSelector> ((:A)-[r]->+(:B) WHERE none(rel IN r WHERE rel:X))
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | 1        |
    Examples:
      | pathSelector        |
      | SHORTEST $one       |
      | SHORTEST $two       |
      | SHORTEST $one GROUP |
      | SHORTEST $two GROUP |
      | ANY $one            |
      | ANY $two            |

  Scenario Outline: Graph pattern predicates are applied after path selector - un-parenthesised
    # ┌─┐   ┌─┐   ┌─┐   ┌─┐
    # │A│──▶│ │──▶│ │──▶│B│
    # └─┘   └─┘   └─┘   └─┘
    #  │       ┌─┐       ▲
    #  └──────▶│ │──X────┘
    #          └─┘
    And having executed:
      """
        CREATE (a:A), (b:B),
          (a)-[:R]->()-[:R]->()-[:R]->(b),
          (a)-[:R]->()-[:X]->(b)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
      MATCH <pathSelector> (:A)-[r]->+(:B) WHERE none(rel IN r WHERE rel:X)
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector        | result   |
      | SHORTEST $one       | 0        |
      | SHORTEST $two       | 1        |
      | SHORTEST $one GROUP | 0        |
      | SHORTEST $two GROUP | 1        |

  Scenario Outline: Graph pattern predicates are applied after path selector - parenthesised
    # ┌─┐   ┌─┐   ┌─┐   ┌─┐
    # │A│──▶│ │──▶│ │──▶│B│
    # └─┘   └─┘   └─┘   └─┘
    #  │       ┌─┐       ▲
    #  └──────▶│ │──X────┘
    #          └─┘
    And having executed:
      """
        CREATE (a:A), (b:B),
          (a)-[:R]->()-[:R]->()-[:R]->(b),
          (a)-[:R]->()-[:X]->(b)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
      MATCH <pathSelector> ((:A)-[r]->+(:B)) WHERE none(rel IN r WHERE rel:X)
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector        | result   |
      | SHORTEST $one       | 0        |
      | SHORTEST $two       | 1        |
      | SHORTEST $one GROUP | 0        |
      | SHORTEST $two GROUP | 1        |

  Scenario Outline: Different path selectors return correct number of paths where multiple paths have same length
    #       ┌─┐
    #     ┌─┤S├─┐
    #     │ └─┘ │
    #    ┌▼┐   ┌▼┐
    #  ┌─┤S├─┬─┤b├─┐
    #  │ └─┘ │ └─┘ │
    # ┌▼┐   ┌▼┐   ┌▼┐
    # │c│   │X│   │e│
    # └┬┘   └┬┘   └┬┘
    #  │ ┌─┐ │ ┌─┐ │
    #  └─►f◄─┴─►g◄─┘
    #    └┬┘   └┬┘
    #     │ ┌─┐ │
    #     └─►T◄─┘
    #       └─┘
    And having executed:
      """
      CREATE (s1:S {n: 's1'}), (s2:S {n: 's2'}), (t1:T {n: 't1'}), (x:X),
        (s1)-[:R]->(s2)-[:R]->(c)-[:R]->(f)-[:R]->(t1),
        (s1)-[:R]->(b)-[:R]->(e)-[:R]->(g)-[:R]->(t1),
        (s2)-[:R]->(x)-[:R]->(f),
        (b)-[:R]->(x)-[:R]->(g)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
      | four  | 4 |
      | six   | 6 |
      | seven | 7 |
    When executing query:
      """
      MATCH p = <pathSelector> (:S) (()--(<filter>))+ (:T)
      WITH [n in nodes(p) | n] as nodes, size(relationships(p)) AS pathLength
      WITH nodes[0].n AS first, nodes[-1].n AS last, pathLength, count(*) AS numMatches
      ORDER BY first, last, pathLength
      WITH first+'-'+last AS partition, collect([pathLength, numMatches]) AS matches
      RETURN collect([partition, matches]) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector           | filter | result                                                                              |
      | SHORTEST $one          |        | [['s1-t1', [[4, 1]]], ['s2-t1', [[3, 1]]]]                                          |
      | SHORTEST $six          |        | [['s1-t1', [[4, 6]]], ['s2-t1', [[3, 3], [5, 3]]]]                                  |
      | SHORTEST $seven        |        | [['s1-t1', [[4, 6], [6, 1]]], ['s2-t1', [[3, 3], [5, 4]]]]                          |
      | SHORTEST $one GROUP    |        | [['s1-t1', [[4, 6]]], ['s2-t1', [[3, 3]]]]                                          |
      | SHORTEST $four GROUPS  |        | [['s1-t1', [[4, 6], [6, 4], [8, 6]]], ['s2-t1', [[3, 3], [5, 5], [7, 6], [9, 10]]]] |
      | SHORTEST $one          | :!X    | [['s1-t1', [[4, 1]]], ['s2-t1', [[3, 1]]]]                                          |
      | SHORTEST $two          | :!X    | [['s1-t1', [[4, 2]]], ['s2-t1', [[3, 1], [5, 1]]]]                                  |
      | SHORTEST $three        | :!X    | [['s1-t1', [[4, 2]]], ['s2-t1', [[3, 1], [5, 1]]]]                                  |
      | SHORTEST $one GROUP    | :!X    | [['s1-t1', [[4, 2]]], ['s2-t1', [[3, 1]]]]                                          |
      | SHORTEST $two GROUPS   | :!X    | [['s1-t1', [[4, 2]]], ['s2-t1', [[3, 1], [5, 1]]]]                                  |
      | SHORTEST $three GROUPS | :!X    | [['s1-t1', [[4, 2]]], ['s2-t1', [[3, 1], [5, 1]]]]                                  |

  Scenario Outline: ANY path selectors return correct number of paths where multiple paths have same length
    #       ┌─┐
    #     ┌─┤S├─┐
    #     │ └─┘ │
    #    ┌▼┐   ┌▼┐
    #  ┌─┤S├─┬─┤b├─┐
    #  │ └─┘ │ └─┘ │
    # ┌▼┐   ┌▼┐   ┌▼┐
    # │c│   │X│   │e│
    # └┬┘   └┬┘   └┬┘
    #  │ ┌─┐ │ ┌─┐ │
    #  └─►f◄─┴─►g◄─┘
    #    └┬┘   └┬┘
    #     │ ┌─┐ │
    #     └─►T◄─┘
    #       └─┘
    And having executed:
      """
      CREATE (s1:S {n: 's1'}), (t1:T {n: 't1'}), (s2:S  {n: 's2'}), (x:X),
        (s1)-[:R]->(s2)-[:R]->(c)-[:R]->(f)-[:R]->(t1),
        (s1)-[:R]->(b)-[:R]->(e)-[:R]->(g)-[:R]->(t1),
        (s2)-[:R]->(x)-[:R]->(f),
        (b)-[:R]->(x)-[:R]->(g)
      """
    And parameters are:
      | one         | 1  |
      | seventeen   | 17 |
    When executing query:
      """
      MATCH p = <pathSelector> (:S) (()--(<filter>))+ (:T)
      WITH [n in nodes(p) | n] as nodes
      WITH nodes[0].n + '-' + nodes[-1].n AS partition, count(*) AS numMatches
      ORDER BY partition
      RETURN collect([partition, numMatches]) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector    | filter | result                         |
      | ANY $one        | :!X    | [['s1-t1', 1], ['s2-t1', 1]]   |
      | ANY $seventeen  | :!X    | [['s1-t1', 2], ['s2-t1', 2]]   |
      | ANY $one        |        | [['s1-t1', 1], ['s2-t1', 1]]   |
      | ANY $one PATH   |        | [['s1-t1', 1], ['s2-t1', 1]]   |
      | ANY $one PATHS  |        | [['s1-t1', 1], ['s2-t1', 1]]   |
      | ANY $seventeen  |        | [['s1-t1', 16], ['s2-t1', 17]] |
      | ANY $one        | :!X    | [['s1-t1', 1], ['s2-t1', 1]]   |
      | ANY $seventeen  | :!X    | [['s1-t1', 2], ['s2-t1', 2]]   |

  Scenario Outline: Return correct paths under different path selectors where there are multiple pairs of nodes
    #              ┌─┐
    #          ┌──▶│5│────┐
    #          │   └─┘    │
    #          │          ▼
    # ┌───┐   ┌─┐       ┌───┐   ┌───┐
    # │A 1│──▶│4│──────▶│B 6│──▶│B 7│
    # └───┘   └─┘       └───┘   └───┘
    #          ▲
    #          │
    # ┌───┐   ┌─┐
    # │A 2│──▶│3│
    # └───┘   └─┘
    And having executed:
      """
      CREATE (n1:A {p: 1}), (n2:A {p: 2}), (n3 {p: 3}), (n4 {p: 4}), (n5 {p: 5}),
        (n6:B {p: 6}), (n7:B {p: 7}),
        (n1)-[:R]->(n4)-[:R]->(n5)-[:R]->(n6)-[:R]->(n7),
        (n2)-[:R]->(n3)-[:R]->(n4)-[:R]->(n6)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
      MATCH p = <pathSelector> (:A)-->+(:B)
      WITH nodes(p) AS n ORDER BY head(n).p, size(n), head(reverse(n)).p
      RETURN collect([m IN n | m.p]) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector           | result                                                                                                                       |
      | SHORTEST $one          | [[1, 4, 6], [1, 4, 6, 7], [2, 3, 4, 6], [2, 3, 4, 6, 7]]                                                                     |
      | SHORTEST $two          | [[1, 4, 6], [1, 4, 5, 6], [1, 4, 6, 7], [1, 4, 5, 6, 7], [2, 3, 4, 6], [2, 3, 4, 5, 6], [2, 3, 4, 6, 7], [2, 3, 4, 5, 6, 7]] |
      | SHORTEST $three        | [[1, 4, 6], [1, 4, 5, 6], [1, 4, 6, 7], [1, 4, 5, 6, 7], [2, 3, 4, 6], [2, 3, 4, 5, 6], [2, 3, 4, 6, 7], [2, 3, 4, 5, 6, 7]] |
      | SHORTEST $one GROUP    | [[1, 4, 6], [1, 4, 6, 7], [2, 3, 4, 6], [2, 3, 4, 6, 7]]                                                                     |
      | SHORTEST $two GROUPS   | [[1, 4, 6], [1, 4, 5, 6], [1, 4, 6, 7], [1, 4, 5, 6, 7], [2, 3, 4, 6], [2, 3, 4, 5, 6], [2, 3, 4, 6, 7], [2, 3, 4, 5, 6, 7]] |
      | SHORTEST $three GROUPS | [[1, 4, 6], [1, 4, 5, 6], [1, 4, 6, 7], [1, 4, 5, 6, 7], [2, 3, 4, 6], [2, 3, 4, 5, 6], [2, 3, 4, 6, 7], [2, 3, 4, 5, 6, 7]] |

  Scenario Outline: OPTIONAL MATCH does not reduce cardinality under different path selectors
    # ┌─┐          ┌─┐
    # │A│─────────▶│B│
    # └─┘          └─┘
    #  │     ┌─┐    ▲
    #  └────▶│ │────┘
    #        └─┘
    And having executed:
      """
      CREATE (a:A)-[:R]->()-[:R]->(:B)<-[:R]-(a)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
      MATCH (a:A), (b:B)
      OPTIONAL MATCH <pathSelector> (a)-[r:<type>]->+(b)
      WITH * ORDER BY size(r)
      RETURN collect([a, r, b]) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector           | type | result                                              |
      | SHORTEST $one          | R    | [[(:A), [[:R]], (:B)]]                              |
      | SHORTEST $two          | R    | [[(:A), [[:R]], (:B)], [(:A), [[:R], [:R]], (:B)]]  |
      | SHORTEST $three        | R    | [[(:A), [[:R]], (:B)], [(:A), [[:R], [:R]], (:B)]]  |
      | SHORTEST $one GROUP    | R    | [[(:A), [[:R]], (:B)]]                              |
      | SHORTEST $two GROUPS   | R    | [[(:A), [[:R]], (:B)], [(:A), [[:R], [:R]], (:B)]]  |
      | SHORTEST $three GROUPS | R    | [[(:A), [[:R]], (:B)], [(:A), [[:R], [:R]], (:B)]]  |
      | SHORTEST $one          | T    | [[(:A), null, (:B)]]                                |
      | SHORTEST $one GROUP    | T    | [[(:A), null, (:B)]]                                |

  Scenario Outline: Find paths with two concatenated QPP under different path selectors
    # ┌──┐    ┌─┐      ┌──┐    ┌──┐
    # │A1├─R─►│2├──R──►│B4├─T─►│B5│
    # └──┘    └┬┘      └──┘    └──┘
    #          R  ┌─┐   ▲
    #          └─►│3├─T─┘
    #             └─┘
    And having executed:
      """
      CREATE (n1:A {p: 1})-[:R]->(n2 {p: 2})-[:R]->(n4:B {p: 4})-[:T]->(n5:B {p: 5}),
        (n2)-[:R]->(n3 {p: 3})-[:T]->(n4)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
      MATCH p = <pathSelector> (:A)-[:R]->+()-[:T]->*(:B)
      WITH nodes(p) AS n ORDER BY size(n), last(n).p
      RETURN collect([m IN n | m.p]) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector           | result                                                   |
      | SHORTEST $one          | [[1, 2, 4], [1, 2, 4, 5]]                                |
      | SHORTEST $two          | [[1, 2, 4], [1, 2, 3, 4], [1, 2, 4, 5], [1, 2, 3, 4, 5]] |
      | SHORTEST $three        | [[1, 2, 4], [1, 2, 3, 4], [1, 2, 4, 5], [1, 2, 3, 4, 5]] |
      | SHORTEST $one GROUP    | [[1, 2, 4], [1, 2, 4, 5]]                                |
      | SHORTEST $two GROUPS   | [[1, 2, 4], [1, 2, 3, 4], [1, 2, 4, 5], [1, 2, 3, 4, 5]] |
      | SHORTEST $three GROUPS | [[1, 2, 4], [1, 2, 3, 4], [1, 2, 4, 5], [1, 2, 3, 4, 5]] |

  Scenario Outline: Find paths under different path selectors with QPP that contains a rigid path size greater than one
    #      ┌─┐  ┌─┐
    #  ┌──►│A├─►│B├────────┐
    #  │   └─┘  └─┘        │
    #  │                   ▼
    # ┌┴┐  ┌─┐  ┌─┐  ┌─┐  ┌──┐
    # │S├─►│A├─►│B├─►│A├─►│BT│◄─┐
    # └┬┘  └─┘  └─┘  └─┘  └──┘  │
    #  │                        │
    #  │   ┌─┐  ┌─┐  ┌─┐  ┌─┐  ┌┴┐
    #  └──►│A├─►│B├─►│A├─►│B├─►│A│
    #      └─┘  └─┘  └─┘  └─┘  └─┘
    And having executed:
      """
      CREATE (s:S)-[:R]->(:A)-[:R]->(:B)-[:R]->(:A)-[:R]->(t:B:T),
        (s)-[:R]->(:A)-[:R]->(:B)-[:R]->(:A)-[:R]->(:B)-[:R]->(:A)-[:R]->(t),
        (s)-[:R]->(:A)-[:R]->(:B)-[:R]->(t)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
      MATCH p = <pathSelector> (:S)(()-->(:A)-->(:B))+(:T)
      WITH nodes(p) AS n ORDER BY size(n)
      RETURN collect(n) AS n
      """
    Then the result should be, in any order:
      | n   |
      | <n> |
    Examples:
      | pathSelector         | n                                                                                |
      | SHORTEST $one        | [[(:S), (:A), (:B), (:A), (:B:T)]]                                               |
      | SHORTEST $two        | [[(:S), (:A), (:B), (:A), (:B:T)], [(:S), (:A), (:B), (:A), (:B), (:A), (:B:T)]] |
      | SHORTEST $three      | [[(:S), (:A), (:B), (:A), (:B:T)], [(:S), (:A), (:B), (:A), (:B), (:A), (:B:T)]] |
      | SHORTEST $one GROUP  | [[(:S), (:A), (:B), (:A), (:B:T)]]                                               |
      | SHORTEST $two GROUPS | [[(:S), (:A), (:B), (:A), (:B:T)], [(:S), (:A), (:B), (:A), (:B), (:A), (:B:T)]] |

  Scenario Outline: Find paths under different path selectors with fixed path concatenated with QPP
    #            ┌─────T─┐┌────T──┐
    #            ▼       │▼       │
    # ┌──┐     ┌──┐     ┌┘─┐     ┌┴┐
    # │A1├──T─►│B2├─S──►│B3├─R──►│4│
    # └──┘     └┬─┘     └──┘     └─┘
    #           R        ▲
    #           │        │
    #           │  ┌─┐   R
    #           └─►│5├───┘
    #              └─┘
    And having executed:
      """
      CREATE (n1:A {p: 1})-[:T]->(n2:B {p: 2})-[:S]->(n3:B {p: 3})-[:R]->(n4 {p: 4}),
        (n2)-[:R]->(n5 {p: 5})-[:R]->(n3), (n4)-[:T]->(n3)-[:T]->(n2)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
      MATCH p = <pathSelector> (:A)-[:!S]->*()-[:T]->(:B)
      WITH nodes(p) AS n ORDER BY size(n), last(n).p
      RETURN collect([m IN n | m.p]) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector           | result                                                               |
      | SHORTEST $one          | [[1, 2], [1, 2, 5, 3, 4, 3]]                                         |
      | SHORTEST $two          | [[1, 2], [1, 2, 5, 3, 2], [1, 2, 5, 3, 4, 3]]                        |
      | SHORTEST $three        | [[1, 2], [1, 2, 5, 3, 2], [1, 2, 5, 3, 4, 3], [1, 2, 5, 3, 4, 3, 2]] |
      | SHORTEST $one GROUP    | [[1, 2], [1, 2, 5, 3, 4, 3]]                                         |
      | SHORTEST $two GROUPS   | [[1, 2], [1, 2, 5, 3, 2], [1, 2, 5, 3, 4, 3]]                        |
      | SHORTEST $three GROUPS | [[1, 2], [1, 2, 5, 3, 2], [1, 2, 5, 3, 4, 3], [1, 2, 5, 3, 4, 3, 2]] |

  Scenario Outline: Find ANY path under different path selectors with fixed path concatenated with QPP
    #            ┌─────T─┐┌────T──┐
    #            ▼       │▼       │
    # ┌──┐     ┌──┐     ┌┘─┐     ┌┴┐
    # │A1├──T─►│B2├─S──►│B3├─R──►│4│
    # └──┘     └┬─┘     └──┘     └─┘
    #           R        ▲
    #           │        │
    #           │  ┌─┐   R
    #           └─►│5├───┘
    #              └─┘
    And having executed:
      """
      CREATE (n1:A {p: 1})-[:T]->(n2:B {p: 2})-[:S]->(n3:B {p: 3})-[:R]->(n4 {p: 4}),
        (n2)-[:R]->(n5 {p: 5})-[:R]->(n3), (n4)-[:T]->(n3)-[:T]->(n2)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
      | four  | 4 |
    When executing query:
      """
      MATCH <pathSelector> (:A)-[:!S]->*()-[:T]->(:B)
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector       | result  |
      | ANY $one           | 2       |
      | ANY $two           | 3       |
      | ANY $three         | 4       |
      | ANY $four          | 4       |

  Scenario Outline: Find shortest simple cycle under different path selectors
    #   ┌─────────────────────────┐
    #   ▼                         │
    # ┌──┐     ┌──┐     ┌──┐     ┌┴─┐
    # │A1├────►│B2├────►│A3├────►│B4│
    # └──┘     └──┘     └──┘     └┬─┘
    #   ▲                         │
    #   │      ┌──┐     ┌──┐      │
    #   └──────┤B6│◄────┤A5│◄─────┘
    #          └──┘     └──┘
    And having executed:
      """
        CREATE (n1:A {p: 1})-[:R]->(n2:B {p: 2})-[:R]->(n3:A {p: 3})-[:R]->(n4:B {p: 4})-[:R]->(n1),
          (n4)-[:R]->(n5:A {p: 5})-[:R]->(n6:B {p: 6})-[:R]->(n1)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
        MATCH p = <pathSelector> (n {p: 1})(()-->(:B)-->(:A))+(n)
        WITH nodes(p) AS n ORDER BY size(n), last(n).p
        RETURN collect([m IN n | m.p]) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector           | result                                   |
      | SHORTEST $one          | [[1, 2, 3, 4, 1]]                        |
      | SHORTEST $two          | [[1, 2, 3, 4, 1], [1, 2, 3, 4, 5, 6, 1]] |
      | SHORTEST $three        | [[1, 2, 3, 4, 1], [1, 2, 3, 4, 5, 6, 1]] |
      | SHORTEST $one GROUP    | [[1, 2, 3, 4, 1]]                        |
      | SHORTEST $two GROUPS   | [[1, 2, 3, 4, 1], [1, 2, 3, 4, 5, 6, 1]] |
      | SHORTEST $three GROUPS | [[1, 2, 3, 4, 1], [1, 2, 3, 4, 5, 6, 1]] |

  Scenario Outline: Lower bound of quantifier prunes some shortest paths under different path selectors
    #  ┌──┐     ┌──┐     ┌──┐     ┌──┐     ┌──┐
    #  │A1├────►│ 2├────►│B3├────►│B4├────►│B7│
    #  └┬─┘     └──┘     └──┘     └──┘     └──┘
    #   │                 ▲
    #   │  ┌──┐     ┌──┐  │
    #   └─►│ 5├────►│ 6├──┘
    #      └──┘     └──┘
    And having executed:
      """
        CREATE (n1:A {p: 1})-[:R]->(n2 {p: 2})-[:R]->(n3:B {p: 3})-[:R]->(n4:B {p: 4})-[:R]->(n7:B {p: 7}),
          (n1)-[:R]->(n5 {p: 5})-[:R]->(n6 {p: 6})-[:R]->(n3)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
        MATCH p = <pathSelector> (:A)-->{4,}(:B)
        WITH nodes(p) AS n ORDER BY size(n), last(n).p
        RETURN collect([m IN n | m.p]) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector           | result                                                 |
      | SHORTEST $one          | [[1, 5, 6, 3, 4], [1, 2, 3, 4, 7]]                     |
      | SHORTEST $two          | [[1, 5, 6, 3, 4], [1, 2, 3, 4, 7], [1, 5, 6, 3, 4, 7]] |
      | SHORTEST $three        | [[1, 5, 6, 3, 4], [1, 2, 3, 4, 7], [1, 5, 6, 3, 4, 7]] |
      | SHORTEST $one GROUP    | [[1, 5, 6, 3, 4], [1, 2, 3, 4, 7]]                     |
      | SHORTEST $two GROUPS   | [[1, 5, 6, 3, 4], [1, 2, 3, 4, 7], [1, 5, 6, 3, 4, 7]] |
      | SHORTEST $three GROUPS | [[1, 5, 6, 3, 4], [1, 2, 3, 4, 7], [1, 5, 6, 3, 4, 7]] |

  Scenario Outline: Lower bound of quantifier prunes ANY paths under different path selectors
    #  ┌──┐     ┌──┐     ┌──┐     ┌──┐     ┌──┐
    #  │A1├────►│ 2├────►│B3├────►│B4├────►│B7│
    #  └┬─┘     └──┘     └──┘     └──┘     └──┘
    #   │                 ▲
    #   │  ┌──┐     ┌──┐  │
    #   └─►│ 5├────►│ 6├──┘
    #      └──┘     └──┘
    And having executed:
      """
        CREATE (n1:A {p: 1})-[:R]->(n2 {p: 2})-[:R]->(n3:B {p: 3})-[:R]->(n4:B {p: 4})-[:R]->(n7:B {p: 7}),
          (n1)-[:R]->(n5 {p: 5})-[:R]->(n6 {p: 6})-[:R]->(n3)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
        MATCH <pathSelector> (:A)-->{4,}(:B)
        RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pathSelector       | result |
      | ANY $one           | 2      |
      | ANY $two           | 3      |
      | ANY $three         | 3      |

  Scenario Outline: Path selectors can be used in EXISTS, COLLECT and COUNT
    #            ┌──┐     ┌──┐
    #     ┌─────►│ 4├────►│B5│
    #     │      └┬─┘     └──┘
    #     │       ▼
    #   ┌─┴┐     ┌──┐     ┌──┐
    # ┌─┤A1├────►│ 2├────►│B3│
    # │ └──┘     └──┘     └──┘
    # │           ▲
    # │ ┌──┐      │
    # └►│B6├──────┘
    #   └──┘
    And having executed:
      """
      CREATE (n1:A {p: 1})-[:R]->(n2 {p: 2})-[:R]->(n3:B {p: 3}),
        (n1)-[:R]->(n4 {p: 4})-[:R]->(n5:B {p: 5}),
        (n4)-[:R]->(n2),
        (n1)-[:R]->(n6:B {p: 6})-[:R]->(n2)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
      | four  | 4 |
    When executing query:
      """
      MATCH (m:A)
      RETURN <subqueryType> {
        MATCH p = <pathSelector> (m)-[r]->+(n:B)
        RETURN reduce(acc = '', n IN nodes(p) | acc + n.p) AS nodes
        ORDER BY size(r), nodes
      } AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | subqueryType | pathSelector           | result                                                 |
      | EXISTS       | SHORTEST $one          | true                                                   |
      | COUNT        | SHORTEST $one          | 3                                                      |
      | COUNT        | SHORTEST $two          | 4                                                      |
      | COUNT        | SHORTEST $three        | 5                                                      |
      | COUNT        | SHORTEST $four         | 5                                                      |
      | COUNT        | SHORTEST $one GROUP    | 3                                                      |
      | COUNT        | SHORTEST $two GROUPS   | 5                                                      |
      | COUNT        | SHORTEST $three GROUPS | 5                                                      |
      | COUNT        | SHORTEST $four GROUPS  | 5                                                      |
      | COLLECT      | SHORTEST $one          | ['16', '123', '145']                                   |
      | COLLECT      | SHORTEST $three        | ['16', '123', '145', '1423', '1623']                   |
      | COLLECT      | SHORTEST $four         | ['16', '123', '145', '1423', '1623']                   |
      | COLLECT      | SHORTEST $one GROUP    | ['16', '123', '145']                                   |
      | COLLECT      | SHORTEST $two GROUPS   | ['16', '123', '145', '1423', '1623']                   |
      | COLLECT      | SHORTEST $three GROUPS | ['16', '123', '145', '1423', '1623']                   |
      | COLLECT      | SHORTEST $four GROUPS  | ['16', '123', '145', '1423', '1623']                   |

  Scenario Outline: ANY path selectors can be used in EXISTS, COLLECT and COUNT
    #            ┌──┐     ┌──┐
    #     ┌─────►│ 4├────►│B5│
    #     │      └┬─┘     └──┘
    #     │       ▼
    #   ┌─┴┐     ┌──┐     ┌──┐
    # ┌─┤A1├────►│ 2├────►│B3│
    # │ └──┘     └──┘     └──┘
    # │           ▲
    # │ ┌──┐      │
    # └►│B6├──────┘
    #   └──┘
    And having executed:
      """
      CREATE (n1:A {p: 1})-[:R]->(n2 {p: 2})-[:R]->(n3:B {p: 3}),
        (n1)-[:R]->(n4 {p: 4})-[:R]->(n5:B {p: 5}),
        (n4)-[:R]->(n2),
        (n1)-[:R]->(n6:B {p: 6})-[:R]->(n2)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
      | four  | 4 |
    When executing query:
      """
      MATCH (m:A)
      RETURN <subqueryType> {
        MATCH <pathSelector> (m)-[r]->+(n:B)
        RETURN n.p ORDER BY n.p
      } AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | subqueryType | pathSelector       | result                                                 |
      | EXISTS       | ANY $one           | true                                                   |
      | COUNT        | ANY $one           | 3                                                      |
      | COUNT        | ANY $two           | 4                                                      |
      | COUNT        | ANY $three         | 5                                                      |
      | COUNT        | ANY $four          | 5                                                      |
      | COLLECT      | ANY $one           | [3, 5, 6]                                              |
      | COLLECT      | ANY $two           | [3, 3, 5, 6]                                           |
      | COLLECT      | ANY $three         | [3, 3, 3, 5, 6]                                        |
      | COLLECT      | ANY $four          | [3, 3, 3, 5, 6]                                        |

  Scenario Outline: Only one selective path pattern allowed in graph pattern (CIP-60)
    When executing query:
      """
        MATCH p = <pathSelector1> (n0:A)-->*(n1)-->*(n2:C), <pathSelector2> (n1)-->+(:E)
        RETURN *
      """
    Then a SyntaxError should be raised at compile time: *
    Examples:
      | pathSelector1        | pathSelector2      |
      | SHORTEST $one        |                    |
      | SHORTEST $one GROUP  |                    |
      | SHORTEST $one        | ALL                |
      | SHORTEST $one GROUP  | ALL                |
      | SHORTEST $one        | ALL SHORTEST       |
      | SHORTEST $one        | SHORTEST GROUP     |
      | SHORTEST $two GROUPS | SHORTEST GROUP     |

  Scenario Outline: Selective path patterns can be combined when in separate MATCH clauses
    #       ┌────────────┐
    #      ┌┴┐           │
    #  ┌──►│X├─────┐     │
    #  │   └─┘     │     │
    #  │    ▲      │     │
    #  │    │      ▼     ▼
    # ┌┴┐  ┌┴┐    ┌─┐   ┌─┐
    # │A│  │B│    │M├──►│C│
    # └┬┘  └┬┘    └─┘   └─┘
    #  │    │      ▲     ▲
    #  │    ▼      │     │
    #  │   ┌─┐    ┌┴┐    │
    #  └──►│Y├───►│N├────┘
    #      └─┘    └─┘
    And having executed:
      """
        CREATE (a:A)-[:R]->(x:X)-[:R]->(m:M)-[:R]->(c:C),
               (x)-[:R]->(c),
               (a)-[:R]->(y:Y)-[:R]->(n:N)-[:R]->(c),
               (n)-[:R]->(m),
               (b:B)-[:R]->(x),
               (b)-[:R]->(y)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
      | four  | 4 |
    When executing query:
      """
        MATCH p = <pathSelector1> (:A)-->+(x:X|Y)-->+(:C)
        MATCH q = <pathSelector2> (:B)-->+(x)-->+(:C)
        WITH nodes(p) AS np, nodes(q) AS nq
        WITH reduce(acc = '', n IN np | acc + labels(n)[0]) AS Ps,
             reduce(acc = '', n IN nq | acc + labels(n)[0]) AS Qs
        ORDER BY size(np), Ps, size(nq), Qs
        RETURN collect([Ps, Qs]) AS result
      """
    Then the result should be, in any order:
      | result    |
      | <result>  |
    Examples:
      | pathSelector1          | pathSelector2        | result                                                                                                                                           |
      | SHORTEST $one          |                      | [['AXC', 'BXC'], ['AXC', 'BXMC']]                                                                                                                |
      | SHORTEST $four         |                      | [['AXC', 'BXC'], ['AXC', 'BXMC'], ['AXMC', 'BXC'], ['AXMC', 'BXMC'], ['AYNC', 'BYNC'], ['AYNC', 'BYNMC'], ['AYNMC', 'BYNC'], ['AYNMC', 'BYNMC']] |
      | SHORTEST $two GROUPS   |                      | [['AXC', 'BXC'], ['AXC', 'BXMC'], ['AXMC', 'BXC'], ['AXMC', 'BXMC'], ['AYNC', 'BYNC'], ['AYNC', 'BYNMC']]                                        |
      | SHORTEST $three GROUPS |                      | [['AXC', 'BXC'], ['AXC', 'BXMC'], ['AXMC', 'BXC'], ['AXMC', 'BXMC'], ['AYNC', 'BYNC'], ['AYNC', 'BYNMC'], ['AYNMC', 'BYNC'], ['AYNMC', 'BYNMC']] |
      |                        | SHORTEST $one        | [['AXC', 'BXC'], ['AXMC', 'BXC'], ['AYNC', 'BYNC'], ['AYNMC', 'BYNC']]                                                                           |
      |                        | SHORTEST $two        | [['AXC', 'BXC'], ['AXC', 'BXMC'], ['AXMC', 'BXC'], ['AXMC', 'BXMC'], ['AYNC', 'BYNC'], ['AYNC', 'BYNMC'], ['AYNMC', 'BYNC'], ['AYNMC', 'BYNMC']] |
      |                        | SHORTEST $two GROUPS | [['AXC', 'BXC'], ['AXC', 'BXMC'], ['AXMC', 'BXC'], ['AXMC', 'BXMC'], ['AYNC', 'BYNC'], ['AYNC', 'BYNMC'], ['AYNMC', 'BYNC'], ['AYNMC', 'BYNMC']] |
      | ANY SHORTEST           | SHORTEST $two        | [['AXC', 'BXC'], ['AXC', 'BXMC']]                                                                                                                |
      | SHORTEST $four         | ANY SHORTEST         | [['AXC', 'BXC'], ['AXMC', 'BXC'], ['AYNC', 'BYNC'], ['AYNMC', 'BYNC']]                                                                           |
      | SHORTEST $four         | SHORTEST $two        | [['AXC', 'BXC'], ['AXC', 'BXMC'], ['AXMC', 'BXC'], ['AXMC', 'BXMC'], ['AYNC', 'BYNC'], ['AYNC', 'BYNMC'], ['AYNMC', 'BYNC'], ['AYNMC', 'BYNMC']] |
      | ALL SHORTEST           | SHORTEST $two        | [['AXC', 'BXC'], ['AXC', 'BXMC']]                                                                                                                |
      | SHORTEST GROUP         | SHORTEST $two        | [['AXC', 'BXC'], ['AXC', 'BXMC']]                                                                                                                |
      | SHORTEST $three GROUPS | ANY SHORTEST         | [['AXC', 'BXC'], ['AXMC', 'BXC'], ['AYNC', 'BYNC'], ['AYNMC', 'BYNC']]                                                                           |
      | SHORTEST $three GROUPS | SHORTEST $two        | [['AXC', 'BXC'], ['AXC', 'BXMC'], ['AXMC', 'BXC'], ['AXMC', 'BXMC'], ['AYNC', 'BYNC'], ['AYNC', 'BYNMC'], ['AYNMC', 'BYNC'], ['AYNMC', 'BYNMC']] |


  Scenario Outline: Fixed-length patterns allowed with path selectors
    # ┌────┐     ┌───┐     ┌────┐
    # │A a1│────▶│B b│◀────│A a2│
    # └────┘     └───┘     └────┘
    #    │         ▲
    #    │         │
    #    └─────────┘
    And having executed:
      """
        CREATE (a1:A {p: 'a1'})-[:R]->(b:B {p: 'b'}), (a1)-[:R]->(b), (:A {p: 'a2'})-[:R]->(b)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
        MATCH <pathSelector> (a:A)-->(b:B)
        WITH a.p AS ap, b.p AS bp, count(*) AS count ORDER BY a.p, b.p
        RETURN collect([ap, bp, count]) AS result
      """
    Then the result should be, in any order:
      | result    |
      | <result>  |
    Examples:
      | pathSelector         | result                            |
      | SHORTEST $one        | [['a1', 'b', 1], ['a2', 'b', 1]]  |
      | SHORTEST $two        | [['a1', 'b', 2], ['a2', 'b', 1]]  |
      | SHORTEST $three      | [['a1', 'b', 2], ['a2', 'b', 1]]  |
      | SHORTEST $one GROUP  | [['a1', 'b', 2], ['a2', 'b', 1]]  |
      | SHORTEST $two GROUPS | [['a1', 'b', 2], ['a2', 'b', 1]]  |
      | ANY $one             | [['a1', 'b', 1], ['a2', 'b', 1]]  |
      | ANY $two             | [['a1', 'b', 2], ['a2', 'b', 1]]  |

  Scenario Outline: Node pattern only allowed with path selectors
    And having executed:
      """
        CREATE (:A {p: 'a1'}), (:A {p: 'a2'})
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH <pathSelector> (a:A)
        WITH a.p AS ap, count(*) AS count ORDER BY a.p
        RETURN collect([ap, count]) AS result
      """
    Then the result should be, in any order:
      | result                  |
      | [['a1', 1], ['a2', 1]]  |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: PathSelector should handle having nested predicates
    And having executed:
      """
        CREATE (:User)-[:R]->(v:V)-[:S1]->(:W), (v)-[:S2]->(n:N)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH p = <pathSelector> ((u:User) ((a)-[r]->(b))+ (v)-[s]->(w) WHERE (v)-->(:N))
        RETURN p
      """
    Then the result should be, in any order:
      | p                                      |
      | <(:User)-[:R {}]->(:V)-[:S2 {}]->(:N)> |
      | <(:User)-[:R {}]->(:V)-[:S1 {}]->(:W)> |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: PathSelector should accept single node as solution
    And having executed:
      """
        CREATE (:A:B {p: 'a1'})-[:REL]->(:A {p: 'a2'})
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH <pathSelector> (a:A)-->*(:B)
        WITH a.p AS ap, count(*) AS count ORDER BY a.p
        RETURN collect([ap, count]) AS result
      """
    Then the result should be, in any order:
      | result     |
      | [['a1', 1]] |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: PathSelector should not find a path which is created later in the query
    And having executed:
      """
        CREATE ()-[:R]->()
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH p = <pathSelector> ((start)((a)-[r:R]->(b))+(end)) MERGE (start)-[t:R]-(:B) RETURN p
      """
    Then the result should be, in any order:
      | p                 |
      | <()-[:R {}]->()>  |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: PathSelector should handle pre-filter predicates on the whole path
    And having executed:
      """
        CREATE ()-[:R]->()-[:R]->()-[:R]->()-[:R]->()
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH <pathSelector> (p = ((start)((a)-[r:R]->(b))+(end)) WHERE length(p) > 3) RETURN p
      """
    Then the result should be, in any order:
      | p                 |
      | <()-[:R]->()-[:R]->()-[:R]->()-[:R]->()>  |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: Predicate outside parentheses in selective path pattern is a postfilter
    # ┌─┐    ┌─┐              ┌─┐
    # │A│───▶│ │─────X───────▶│B│
    # └─┘    └─┘              └─┘
    #  │     ┌─┐    ┌─┐        ▲
    #  ├────▶│ │───▶│ │────────┤
    #  │     └─┘    └─┘        │
    #  │     ┌─┐    ┌─┐   ┌─┐  │
    #  └────▶│ │───▶│ │──▶│ │──┘
    #        └─┘    └─┘   └─┘
    And having executed:
      """
        CREATE (a:A)-[:R]->()-[:X]->(b:B),
               (a)-[:R]->()-[:R]->()-[:R]->(b),
               (a)-[:R]->()-[:R]->()-[:R]->()-[:R]->(b)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
        MATCH p = <pathSelector> (:A)-->*(:B) WHERE none(r IN relationships(p) WHERE r:X)
        RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result    |
      | <result>  |
    Examples:
      | pathSelector           | result  |
      | SHORTEST $one          | 0       |
      | SHORTEST $two          | 1       |
      | SHORTEST $three        | 2       |
      | SHORTEST $one GROUP    | 0       |
      | SHORTEST $two GROUPS   | 1       |
      | SHORTEST $three GROUPS | 2       |
      | ANY $one               | 0       |
      | ANY $two               | 1       |
      | ANY $three             | 2       |

  Scenario Outline: Predicate not required when making subpath variable declaration
    # ┌─┐     ┌─┐     ┌─┐     ┌─┐
    # │L│────▶│ │────▶│L│────▶│L│
    # └─┘     └─┘     └─┘     └─┘
    #  ▲                       │
    #  │      ┌─┐     ┌─┐      │
    #  └──────│ │◀────│ │◀─────┘
    #         └─┘     └─┘
    And having executed:
      """
        CREATE (a:L)-[:R]->()-[:R]->(b:L)-[:R]->(c:L),
               (a)<-[:R]-()<-[:R]-()<-[:R]-(c)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
        MATCH <pathSelector> (p = (:L)--+(:L))
        WITH length(p) AS pathLength, count(*) AS count ORDER BY pathLength
        RETURN collect([pathLength, count]) AS result
      """
    Then the result should be, in any order:
      | result    |
      | <result>  |
    Examples:
      | pathSelector           | result                                            |
      | SHORTEST $one          | [[1, 2], [2, 2], [3, 2], [6, 3]]                  |
      | SHORTEST $two          | [[1, 2], [2, 2], [3, 4], [4, 2], [5, 2], [6, 6]]  |
      | SHORTEST $three        | [[1, 2], [2, 2], [3, 4], [4, 2], [5, 2], [6, 6]]  |
      | SHORTEST $one GROUP    | [[1, 2], [2, 2], [3, 4], [6, 6]]                  |
      | SHORTEST $two GROUPS   | [[1, 2], [2, 2], [3, 4], [4, 2], [5, 2], [6, 6]]  |
      | SHORTEST $three GROUPS | [[1, 2], [2, 2], [3, 4], [4, 2], [5, 2], [6, 6]]  |
      | ANY $one               | [[1, 2], [2, 2], [3, 2], [6, 3]]                  |
      | ANY $two               | [[1, 2], [2, 2], [3, 4], [4, 2], [5, 2], [6, 6]]  |
      | ANY $three             | [[1, 2], [2, 2], [3, 4], [4, 2], [5, 2], [6, 6]]  |

  Scenario Outline: Disjoint path finding using subpath variables from separate MATCH clauses
    #        ┌─┐
    #  ┌────▶│ │──────────┐
    #  │     └─┘          ▼
    # ┌─┐    ┌─┐         ┌─┐
    # │A│───▶│X│────────▶│B│
    # └─┘    └─┘         └─┘
    #  │     ┌─┐    ┌─┐   ▲
    #  ├────▶│ │───▶│ │───┤
    #  │     └─┘    └─┘   │
    #  │     ┌─┐    ┌─┐   │
    #  └────▶│ │───▶│X│───┘
    #        └─┘    └─┘
    And having executed:
      """
        CREATE (a:A)-[:R]->(:X)-[:R]->(b:B),
               (a)-[:R]->()-[:R]->(b),
               (a)-[:R]->()-[:R]->(:X)-[:R]->(b),
               (a)-[:R]->()-[:R]->()-[:R]->(b)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
      | three | 3 |
    When executing query:
      """
        MATCH <pathSelector> (p = (:A)-->+(:B) WHERE none(n IN nodes(p) WHERE n:X))
        MATCH <pathSelector> (q = (:A)-->+(:B) WHERE q <> p AND length(p) = length(q))
        WITH length(p) AS pathLength,
             size([n IN nodes(p) WHERE n:X]) AS pxCount,
             size([n IN nodes(q) WHERE n:X]) AS qxCount
        ORDER BY pathLength
        RETURN collect([pathLength, pxCount, qxCount]) AS result
      """
    Then the result should be, in any order:
      | result    |
      | <result>  |
    Examples:
      | pathSelector           | result                                            |
      | SHORTEST $one          | [[2, 0, 1]]                                       |
      | SHORTEST $two          | [[2, 0, 1], [3, 0, 1]]                            |
      | SHORTEST $three        | [[2, 0, 1], [3, 0, 1]]                            |
      | SHORTEST $one GROUP    | [[2, 0, 1]]                                       |
      | SHORTEST $two GROUPS   | [[2, 0, 1], [3, 0, 1]]                            |
      | SHORTEST $three GROUPS | [[2, 0, 1], [3, 0, 1]]                            |
      | ANY $one               | [[2, 0, 1]]                                       |
      | ANY $two               | [[2, 0, 1], [3, 0, 1]]                            |
      | ANY $three             | [[2, 0, 1], [3, 0, 1]]                            |

  Scenario Outline: PathSelector with subpath variable should handle legacy var-length
    And having executed:
      """
        CREATE ()-[:R]->()-[:T]->()
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH <pathSelector> (p=()-[*1]->()) RETURN p
      """
    Then the result should be, in any order:
      | p                |
      | <()-[:T {}]->()> |
      | <()-[:R {}]->()> |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: PathSelector with subpath variable should handle legacy var-length with set upper bound
    And having executed:
      """
        CREATE (:A)-[:R]->(:B)-[:T]->(:B)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH <pathSelector> (p=(a:A)-[*0..1]-(b:B)) RETURN p
      """
    Then the result should be, in any order:
      | p                    |
      | <(:A)-[:R {}]->(:B)> |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: PathSelector with subpath variable should handle a previously bound boundary node
    And having executed:
      """
        CREATE (:L)-[:R]->()-[:R]->()
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH (start)
        MATCH <pathSelector> (p = (start:L)((a)-[r:R]->(b))+(end)) RETURN p
      """
    Then the result should be, in any order:
      | p                              |
      | <(:L)-[:R {}]->()>             |
      | <(:L)-[:R {}]->()-[:R {}]->()> |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: PathSelector with subpath variable should handle a previously bound relationship
    And having executed:
      """
        CREATE ()-[:R]->()-[:T]->()
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH ()-[r]->()
        MATCH <pathSelector> (p = (start)-[r:R]->(a)((b)-[]->(c))+(end)) RETURN p
      """
    Then the result should be, in any order:
      | p                              |
      | <()-[:R {}]->()-[:T {}]->()>   |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: Subpath variable not allowed in a quantified path pattern
    When executing query:
      """
        MATCH <pathSearchPrefix> (p = (:A)--(:B))+
        RETURN *
      """
    And parameters are:
      | one   | 1 |
    Then a SyntaxError should be raised at compile time: *
    Examples:
      | pathSearchPrefix     |
      | SHORTEST $one        |
      | ANY $one             |
      | SHORTEST $one GROUP  |
      | ANY $one             |

  Scenario Outline: Subpath variable not allowed in parenthesised path pattern expression that is not the whole path pattern
    And parameters are:
      | one   | 1 |
    When executing query:
      """
        MATCH <pathSearchPrefix> (p = (:A)-[:R]->{,3}(:B)) (e)<-[:S]-(x)
        RETURN p, e, x
      """
    Then a SyntaxError should be raised at compile time: *
    Examples:
      | pathSearchPrefix     |
      | SHORTEST $one        |
      | ANY $one             |
      | SHORTEST $one GROUP  |
      | ANY $one             |

  Scenario Outline: Parenthesised path pattern WHERE clause may not reference a path variable declared in same path pattern
    And parameters are:
      | one   | 1 |
    When executing query:
      """
        MATCH p = <pathSearchPrefix> ((:A)-->+(:B) WHERE length(p) % 2 <> 0)
        RETURN p
      """
    Then a SyntaxError should be raised at compile time: *
    Examples:
      | pathSearchPrefix     |
      | SHORTEST $one        |
      | ANY $one             |
      | SHORTEST $one GROUP  |
      | ANY $one             |

  Scenario Outline: Subpath variable name may not clash with other variable declarations
    And parameters are:
      | one   | 1 |
    When executing query:
      """
        MATCH <firstGraphPattern>
        MATCH <secondGraphPattern>
        RETURN *
      """
    Then a SyntaxError should be raised at compile time: *
    Examples:
      | firstGraphPattern                 | secondGraphPattern             |
      | SHORTEST $one GROUP (p = ()--+()) | (p)                            |
      | SHORTEST $one (p = ()--+())       | ()-[p]-()                      |
      | SHORTEST $one GROUP (p = ()--+()) | ()-[p]-()                      |
      | SHORTEST $one (p = ()--+())       | p = ()--()                     |
      | SHORTEST $one GROUP (p = ()--+()) | p = ()--()                     |
      | SHORTEST $one (p = ()--+())       | ANY (p = ()--+())              |
      | SHORTEST $one GROUP (p = ()--+()) | ANY (p = ()--+())              |

  Scenario Outline: Path variable and subpath variable declared in same path pattern may not have same name
    And parameters are:
      | one   | 1 |
    When executing query:
      """
        MATCH p = <pathSearchPrefix> (p = (:A)-->+(:B))
        RETURN p
      """
    Then a SyntaxError should be raised at compile time: *
    Examples:
      | pathSearchPrefix     |
      | SHORTEST $one        |
      | ANY $one             |
      | SHORTEST $one GROUP  |
      | ANY $one             |

  Scenario Outline: Path variable and subpath variable declared in same path pattern bind to same path
    And having executed:
      """
        CREATE (:A)-[:R]->()-[:R]->(:B)
      """
    And parameters are:
      | one   | 1 |
    When executing query:
      """
        MATCH p = <pathSearchPrefix> (q = (:A)-->+(:B))
        RETURN p = q AS result
      """
    Then the result should be, in any order:
      | result  |
      | true    |
    Examples:
      | pathSearchPrefix     |
      | SHORTEST $one        |
      | ANY $one             |
      | SHORTEST $one GROUP  |
      | ANY $one             |

  Scenario Outline: Should support a shortest path pattern with a predicate on several entities inside a QPP
    And having executed:
      """
        CREATE (:User {prop: 4})-[:R]->(:B)-[:R]->({prop: 5})
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH p = <pathSelector> (u:User)(((n)-[r]->(c:B)-->(m)) WHERE n.prop <= m.prop)+ (v) RETURN p
      """
    Then the result should be, in any order:
      | p                                                      |
      | <(:User {prop: 4})-[:R {}]->(:B)-[:R {}]->({prop: 5})> |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: PathSelector should handle multiple references to the same variable in a pattern
    And having executed:
      """
        CREATE (a:A:B)-[:R]->()-[:S]->(a)
      """
    And parameters are:
      | one   | 1 |
      | two   | 2 |
    When executing query:
      """
        MATCH <pathSelector> (p = (start)((a:A)-[]->()-[]->(a:B))+(end)) RETURN p
      """
    Then the result should be, in any order:
      | p                                    |
      | <(:A:B)-[:R {}]->()-[:S {}]->(:A:B)> |
    Examples:
      | pathSelector         |
      | SHORTEST $one        |
      | SHORTEST $two        |
      | SHORTEST $one GROUP  |
      | SHORTEST $two GROUPS |
      | ANY $one             |
      | ANY $two             |

  Scenario Outline: Error on invalid parameter types
    And having executed:
      """
      CREATE (a:A), (b:B), (c:C), (d:D), (x:X),
        (a)-[:R]->(b)-[:R]->(c)-[:R]->(d),
        (a)-[:R]->(x)-[:R]->(d)
      """
    And parameters are:
      | string    | '1'  |
      | boolean   | true  |
      | float     | 1.5  |
    When executing query:
      """
      MATCH p = SHORTEST <path> (:A)-->+(:D)
      WITH nodes(p) AS n ORDER BY size(n)
      RETURN collect(n) AS result
      """
    Then a TypeError should be raised at runtime: *
    Examples:
      | path     |
      | $string  |
      | $boolean |
      | $float   |

  @allowCustomErrors
  Scenario Outline: Error on non positive path integers
    And having executed:
      """
      CREATE (a:A), (b:B), (c:C), (d:D), (x:X),
        (a)-[:R]->(b)-[:R]->(c)-[:R]->(d),
        (a)-[:R]->(x)-[:R]->(d)
      """
    And parameters are:
      | path0   | 0  |
      | path1   | -1 |
    When executing query:
      """
      MATCH p = SHORTEST <path> (:A)-->+(:D)
      WITH nodes(p) AS n ORDER BY size(n)
      RETURN collect(n) AS result
      """
    Then a InvalidArguments should be raised at runtime: *
    Examples:
      | path    |
      | $path0  |
      | $path1  |