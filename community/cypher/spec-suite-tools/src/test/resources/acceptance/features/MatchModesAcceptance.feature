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

Feature: MatchModesAcceptance

  Background:
    Given an empty graph

  Scenario: Multiply-declared relationship variable specifies an equijoin under REPEATABLE ELEMENTS
    And having executed:
      """
      CREATE (:A)-[:R]->(:B)-[:S]->(:C)
      """
    When executing query:
      """
      MATCH REPEATABLE ELEMENTS p = ()-[q]-()-[q]-()
      RETURN [n IN nodes(p) | labels(n)[0]] AS result
      """
    Then the result should be, in any order:
      | result                       |
      | ['A', 'B', 'A']              |
      | ['B', 'A', 'B']              |
      | ['B', 'C', 'B']              |
      | ['C', 'B', 'C']              |

  Scenario Outline: Multiply-declared relationship returns no solutions under DIFFERENT RELATIONSHIPS
    And having executed:
      """
      CREATE (:A)-[:R]->(:B)-[:S]->(:C)
      """
    When executing query:
      """
      MATCH <matchMode> ()-[q]-()-[q]-()
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | matchMode               | result |
      | DIFFERENT RELATIONSHIPS | 0      |
      |                         | 0      |

  Scenario Outline: Repeated relationships only returned under REPEATABLE ELEMENTS
    And having executed:
      """
      CREATE ()-[:R]->()
      """
    When executing query:
      """
      MATCH <matchMode> ()-->()--()
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | matchMode                 | result |
      | DIFFERENT RELATIONSHIPS   | 0      |
      |                           | 0      |
      | REPEATABLE ELEMENTS       | 1      |

  Scenario Outline: QPP under REPEATABLE ELEMENTS returns results if bounded
    Given an empty graph
    And having executed:
      """
      CREATE (:A)-[:R]->(:B)
      """
    When executing query:
      """
      MATCH REPEATABLE ELEMENTS <pattern>
      RETURN count(*) AS result
      """
    Then the result should be, in any order:
      | result   |
      | <result> |
    Examples:
      | pattern                 | result |
      | (:A)--{1,5}()           | 5      |
      | (:A)--{2}()             | 1      |
      | (:A)(()--()){1,5}       | 5      |
      | (:A)(()--()){2}         | 1      |

  Scenario Outline: Unbounded QPP under REPEATABLE ELEMENTS raises SyntaxError
    Given an empty graph
    And having executed:
      """
      CREATE ()-[:R]->()
      """
    When executing query:
      """
      MATCH REPEATABLE ELEMENTS <pathSearch> <pattern>
      RETURN count(*) AS result
      """
    Then a SyntaxError should be raised at compile time: *
    Examples:
      | pattern             | pathSearch       |
      | ()--{1,}()          |                  |
      | ()--+()             |                  |
      | ()--*()             |                  |
      | (()--()){1,}        |                  |
      | (()--())+           |                  |
      | (()--())*           |                  |
      | ()--{1,}()          | ANY              |
      | ()--{1,}()          | ALL              |
      | ()--{1,}()          | SHORTEST 1       |
      | ()--{1,}()          | ALL SHORTEST     |
      | ()--{1,}()          | ANY SHORTEST     |
      | ()--{1,}()          | SHORTEST GROUP   |
      | ()--{1,}()          | SHORTEST 1 GROUP |
