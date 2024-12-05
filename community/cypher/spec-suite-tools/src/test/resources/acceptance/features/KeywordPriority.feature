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

Feature: KeywordPriority

  Scenario Outline: WHERE keyword has priority in node patterns in CREATE
    Given any graph
    When executing query:
      """
      CREATE ( WHERE <filler> )
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         |
      | { }            |
      | { p: 5 }       |
      | WHERE { }      |
      | WHERE { p: 5 } |

  Scenario Outline: WHERE keyword has priority in relationship patterns in CREATE
    Given any graph
    When executing query:
      """
      CREATE ()<left>[ WHERE <filler> ]<right>()
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left | right |
      | { }            | -    | ->    |
      | { }            | <-   | -     |
      | { }            | -    | -     |
      | { p: 5 }       | -    | ->    |
      | { p: 5 }       | <-   | -     |
      | { p: 5 }       | -    | -     |
      | WHERE { }      | -    | ->    |
      | WHERE { }      | <-   | -     |
      | WHERE { }      | -    | -     |
      | WHERE { p: 5 } | -    | ->    |
      | WHERE { p: 5 } | <-   | -     |
      | WHERE { p: 5 } | -    | -     |

  Scenario Outline: WHERE keyword has priority in node patterns in INSERT
    Given any graph
    When executing query:
      """
      INSERT ( WHERE <filler> )
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         |
      | { }            |
      | { p: 5 }       |
      | WHERE { }      |
      | WHERE { p: 5 } |

  Scenario Outline: WHERE keyword has priority in relationship patterns in INSERT
    Given any graph
    When executing query:
      """
      INSERT ()<left>[ WHERE <filler> ]<right>()
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left | right |
      | { }            | -    | ->    |
      | { }            | <-   | -     |
      | { }            | -    | -     |
      | { p: 5 }       | -    | ->    |
      | { p: 5 }       | <-   | -     |
      | { p: 5 }       | -    | -     |
      | WHERE { }      | -    | ->    |
      | WHERE { }      | <-   | -     |
      | WHERE { }      | -    | -     |
      | WHERE { p: 5 } | -    | ->    |
      | WHERE { p: 5 } | <-   | -     |
      | WHERE { p: 5 } | -    | -     |

  Scenario Outline: WHERE keyword has priority in node patterns in MATCH
    Given any graph
    When executing query:
      """
      MATCH ( WHERE <filler> )
      RETURN 1
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         |
      | { }            |
      | { p: 5 }       |
      | WHERE { }      |
      | WHERE { p: 5 } |

  Scenario Outline: WHERE keyword has priority in relationship patterns in MATCH
    Given any graph
    When executing query:
      """
      MATCH ()<left>[ WHERE <filler> ]<right>()
      RETURN 1
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left | right |
      | { }            | -    | ->    |
      | { }            | <-   | -     |
      | { }            | -    | -     |
      | { p: 5 }       | -    | ->    |
      | { p: 5 }       | <-   | -     |
      | { p: 5 }       | -    | -     |
      | WHERE { }      | -    | ->    |
      | WHERE { }      | <-   | -     |
      | WHERE { }      | -    | -     |
      | WHERE { p: 5 } | -    | ->    |
      | WHERE { p: 5 } | <-   | -     |
      | WHERE { p: 5 } | -    | -     |

  Scenario Outline: WHERE keyword has priority in node patterns in MERGE
    Given any graph
    When executing query:
      """
      MERGE ( WHERE <filler> )
      RETURN 1
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         |
      | { }            |
      | { p: 5 }       |
      | WHERE { }      |
      | WHERE { p: 5 } |

  Scenario Outline: WHERE keyword has priority in relationship patterns in MERGE
    Given any graph
    When executing query:
      """
      MERGE ()<left>[ WHERE <filler> ]<right>()
      RETURN 1
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left | right |
      | { }            | -    | ->    |
      | { }            | <-   | -     |
      | { }            | -    | -     |
      | { p: 5 }       | -    | ->    |
      | { p: 5 }       | <-   | -     |
      | { p: 5 }       | -    | -     |
      | WHERE { }      | -    | ->    |
      | WHERE { }      | <-   | -     |
      | WHERE { }      | -    | -     |
      | WHERE { p: 5 } | -    | ->    |
      | WHERE { p: 5 } | <-   | -     |
      | WHERE { p: 5 } | -    | -     |

  Scenario Outline: WHERE keyword has priority in node patterns in EXISTS subquery
    Given any graph
    When executing query:
      """
      RETURN EXISTS { ( WHERE <filler> ) }
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         |
      | { }            |
      | { p: 5 }       |
      | WHERE { }      |
      | WHERE { p: 5 } |

  Scenario Outline: WHERE keyword has priority in relationship patterns in EXISTS subquery
    Given any graph
    When executing query:
      """
      RETURN EXISTS { ()<left>[ WHERE <filler> ]<right>() }
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left | right |
      | { }            | -    | ->    |
      | { }            | <-   | -     |
      | { }            | -    | -     |
      | { p: 5 }       | -    | ->    |
      | { p: 5 }       | <-   | -     |
      | { p: 5 }       | -    | -     |
      | WHERE { }      | -    | ->    |
      | WHERE { }      | <-   | -     |
      | WHERE { }      | -    | -     |
      | WHERE { p: 5 } | -    | ->    |
      | WHERE { p: 5 } | <-   | -     |
      | WHERE { p: 5 } | -    | -     |

  Scenario Outline: WHERE keyword has priority in node patterns in COUNT subquery
    Given any graph
    When executing query:
      """
      RETURN COUNT { ( WHERE <filler> ) }
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         |
      | { }            |
      | { p: 5 }       |
      | WHERE { }      |
      | WHERE { p: 5 } |

  Scenario Outline: WHERE keyword has priority in relationship patterns in COUNT subquery
    Given any graph
    When executing query:
      """
      RETURN COUNT { ()<left>[ WHERE <filler> ]<right>() }
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left | right |
      | { }            | -    | ->    |
      | { }            | <-   | -     |
      | { }            | -    | -     |
      | { p: 5 }       | -    | ->    |
      | { p: 5 }       | <-   | -     |
      | { p: 5 }       | -    | -     |
      | WHERE { }      | -    | ->    |
      | WHERE { }      | <-   | -     |
      | WHERE { }      | -    | -     |
      | WHERE { p: 5 } | -    | ->    |
      | WHERE { p: 5 } | <-   | -     |
      | WHERE { p: 5 } | -    | -     |

  Scenario Outline: WHERE keyword has priority in node patterns in pattern comprehensions
    Given any graph
    When executing query:
      """
      RETURN [<left>( WHERE <filler> )<right> | WHERE.prop]
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left  | right |
      | { }            |       | -->() |
      | { }            | ()<-- |       |
      | { p: 5 }       |       | -->() |
      | { p: 5 }       | ()<-- |       |
      | WHERE { }      |       | -->() |
      | WHERE { }      | ()<-- |       |
      | WHERE { p: 5 } |       | -->() |
      | WHERE { p: 5 } | ()<-- |       |

  Scenario Outline: WHERE keyword has priority in relationship patterns in pattern comprehensions
    Given any graph
    When executing query:
      """
      RETURN [()<left>[ WHERE <filler> ]<right>() | WHERE.prop]
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left | right |
      | { }            | -    | ->    |
      | { }            | <-   | -     |
      | { }            | -    | -     |
      | { p: 5 }       | -    | ->    |
      | { p: 5 }       | <-   | -     |
      | { p: 5 }       | -    | -     |
      | WHERE { }      | -    | ->    |
      | WHERE { }      | <-   | -     |
      | WHERE { }      | -    | -     |
      | WHERE { p: 5 } | -    | ->    |
      | WHERE { p: 5 } | <-   | -     |
      | WHERE { p: 5 } | -    | -     |

  Scenario Outline: WHERE keyword has priority in node patterns in pattern expressions
    Given any graph
    When executing query:
      """
      MATCH (where :Node) WHERE ( WHERE <filler> )-->()
      RETURN 1
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         |
      | { }            |
      | { p: 5 }       |
      | WHERE { }      |
      | WHERE { p: 5 } |

  Scenario Outline: WHERE keyword has priority in relationship patterns in pattern expressions
    Given any graph
    When executing query:
      """
      MATCH ()-[where :REL]->() WHERE ()<left>[ WHERE <filler> ]<right>()
      RETURN 1
      """
    Then a SyntaxError should be raised at compile time: *

    Examples:
      | filler         | left | right |
      | { }            | -    | ->    |
      | { }            | <-   | -     |
      | { }            | -    | -     |
      | { p: 5 }       | -    | ->    |
      | { p: 5 }       | <-   | -     |
      | { p: 5 }       | -    | -     |
      | WHERE { }      | -    | ->    |
      | WHERE { }      | <-   | -     |
      | WHERE { }      | -    | -     |
      | WHERE { p: 5 } | -    | ->    |
      | WHERE { p: 5 } | <-   | -     |
      | WHERE { p: 5 } | -    | -     |

  Scenario Outline: Keywords allowed as labels in patterns in CREATE and INSERT #Example: <keyword>
    Given an empty graph
    When executing query:
      """
      CREATE (c1:<keyword>)-[c3 IS <keyword>]->(c2 IS <keyword>)<-[c4:<keyword>]-(c1)
      INSERT (i1:<keyword>)-[i3 IS <keyword>]->(i2 IS <keyword>)<-[i4:<keyword>]-(i1)
      RETURN c1, c2, c3, c4, i1, i2, i3, i4
      """
    Then the result should be, in any order:
      | c1           | c2           | c3           | c4           | i1           | i2           | i3           | i4           |
      | (:<keyword>) | (:<keyword>) | [:<keyword>] | [:<keyword>] | (:<keyword>) | (:<keyword>) | [:<keyword>] | [:<keyword>] |
    And the side effects should be:
      | +nodes         | 4 |
      | +relationships | 4 |
      | +labels        | 1 |

    Examples:
      | keyword            |
      | ACCESS             |
      | ACTIVE             |
      | ADMIN              |
      | ADMINISTRATOR      |
      | ALIAS              |
      | ALIASES            |
      | ALL_SHORTEST_PATHS |
      | ALL                |
      | ALTER              |
      | AND                |
      | ANY                |
      | ARRAY              |
      | AS                 |
      | ASC                |
      | ASCENDING          |
      | ASSIGN             |
      | AT                 |
      | AUTH               |
      | BINDINGS           |
      | BOOL               |
      | BOOLEAN            |
      | BOOSTED            |
      | BOTH               |
      | BREAK              |
      | BUILT              |
      | BY                 |
      | CALL               |
      | CASCADE            |
      | CASE               |
      | CHANGE             |
      | CIDR               |
      | COLLECT            |
      | COMMAND            |
      | COMMANDS           |
      | COMPOSITE          |
      | CONCURRENT         |
      | CONSTRAINT         |
      | CONSTRAINTS        |
      | CONTAINS           |
      | CONTINUE           |
      | COPY               |
      | COUNT              |
      | CREATE             |
      | CSV                |
      | CURRENT            |
      | DATA               |
      | DATABASE           |
      | DATABASES          |
      | DATE               |
      | DATETIME           |
      | DBMS               |
      | DEALLOCATE         |
      | DEFAULT            |
      | DEFINED            |
      | DELETE             |
      | DENY               |
      | DESC               |
      | DESCENDING         |
      | DESTROY            |
      | DETACH             |
      | DIFFERENT          |
      | DISTINCT           |
      | DRIVER             |
      | DROP               |
      | DRYRUN             |
      | DUMP               |
      | DURATION           |
      | EACH               |
      | EDGE               |
      | ELEMENT            |
      | ELEMENTS           |
      | ELSE               |
      | ENABLE             |
      | ENCRYPTED          |
      | END                |
      | ENDS               |
      | ERROR              |
      | EXECUTABLE         |
      | EXECUTE            |
      | EXIST              |
      | EXISTENCE          |
      | EXISTS             |
      | FAIL               |
      | FALSE              |
      | FIELDTERMINATOR    |
      | FINISH             |
      | FLOAT              |
      | FOREACH            |
      | FOR                |
      | FROM               |
      | FULLTEXT           |
      | FUNCTION           |
      | FUNCTIONS          |
      | GRANT              |
      | GRAPH              |
      | GRAPHS             |
      | GROUP              |
      | GROUPS             |
      | HEADERS            |
      | HOME               |
      | ID                 |
      | IF                 |
      | IMMUTABLE          |
      | IMPERSONATE        |
      | IN                 |
      | INDEX              |
      | INDEXES            |
      | INF                |
      | INFINITY           |
      | INSERT             |
      | INT                |
      | INTEGER            |
      | IS                 |
      | JOIN               |
      | KEY                |
      | LABEL              |
      | LABELS             |
      | LEADING            |
      | LIMITROWS          |
      | LIST               |
      | LOAD               |
      | LOCAL              |
      | LOOKUP             |
      | MATCH              |
      | MANAGEMENT         |
      | MAP                |
      | MERGE              |
      | NAME               |
      | NAMES              |
      | NAN                |
      | NEW                |
      | NFC                |
      | NFD                |
      | NFKC               |
      | NFKD               |
      | NODE               |
      | NODETACH           |
      | NODES              |
      | NONE               |
      | NORMALIZE          |
      | NORMALIZED         |
      | NOT                |
      | NOTHING            |
      | NOWAIT             |
      | NULL               |
      | OF                 |
      | OFFSET             |
      | ON                 |
      | ONLY               |
      | OPTIONAL           |
      | OPTIONS            |
      | OPTION             |
      | OR                 |
      | ORDER              |
      | PASSWORD           |
      | PASSWORDS          |
      | PATH               |
      | PATHS              |
      | PLAINTEXT          |
      | POINT              |
      | POPULATED          |
      | PRIMARY            |
      | PRIMARIES          |
      | PRIVILEGE          |
      | PRIVILEGES         |
      | PROCEDURE          |
      | PROCEDURES         |
      | PROPERTIES         |
      | PROPERTY           |
      | PROVIDER           |
      | PROVIDERS          |
      | RANGE              |
      | READ               |
      | REALLOCATE         |
      | REDUCE             |
      | REL                |
      | RELATIONSHIP       |
      | RELATIONSHIPS      |
      | REMOVE             |
      | RENAME             |
      | REPEATABLE         |
      | REPLACE            |
      | REPORT             |
      | REQUIRE            |
      | REQUIRED           |
      | RESTRICT           |
      | RETURN             |
      | REVOKE             |
      | ROLE               |
      | ROLES              |
      | ROW                |
      | ROWS               |
      | SCAN               |
      | SECONDARY          |
      | SECONDARIES        |
      | SEC                |
      | SECOND             |
      | SECONDS            |
      | SEEK               |
      | SERVER             |
      | SERVERS            |
      | SET                |
      | SETTING            |
      | SETTINGS           |
      | SHORTEST           |
      | SHORTEST_PATH      |
      | SHOW               |
      | SIGNED             |
      | SINGLE             |
      | SKIPROWS           |
      | START              |
      | STARTS             |
      | STATUS             |
      | STOP               |
      | VARCHAR            |
      | STRING             |
      | SUPPORTED          |
      | SUSPENDED          |
      | TARGET             |
      | TERMINATE          |
      | TEXT               |
      | THEN               |
      | TIME               |
      | TIMESTAMP          |
      | TIMEZONE           |
      | TO                 |
      | TOPOLOGY           |
      | TRAILING           |
      | TRANSACTION        |
      | TRANSACTIONS       |
      | TRAVERSE           |
      | TRIM               |
      | TRUE               |
      | TYPE               |
      | TYPED              |
      | TYPES              |
      | UNION              |
      | UNIQUE             |
      | UNIQUENESS         |
      | UNWIND             |
      | URL                |
      | USE                |
      | USER               |
      | USERS              |
      | USING              |
      | VALUE              |
      | VECTOR             |
      | VERTEX             |
      | WAIT               |
      | WHEN               |
      | WHERE              |
      | WITH               |
      | WITHOUT            |
      | WRITE              |
      | XOR                |
      | YIELD              |
      | ZONE               |
      | ZONED              |

  Scenario Outline: Keywords allowed as labels in patterns in MERGE #Example: <keyword>
    Given an empty graph
    When executing query:
      """
      MERGE (m1:<keyword>)
      MERGE (m2 IS <keyword>)
      MERGE (m1)-[r1:<keyword>]->(m2)
      MERGE (m2)<-[r2 IS <keyword>]-(m1)
      RETURN m1, m2, r1, r2
      """
    Then the result should be, in any order:
      | m1           | m2           | r1           | r2           |
      | (:<keyword>) | (:<keyword>) | [:<keyword>] | [:<keyword>] |
    And the side effects should be:
      | +nodes         | 1 |
      | +relationships | 1 |
      | +labels        | 1 |

    Examples:
      | keyword            |
      | ACCESS             |
      | ACTIVE             |
      | ADMIN              |
      | ADMINISTRATOR      |
      | ALIAS              |
      | ALIASES            |
      | ALL_SHORTEST_PATHS |
      | ALL                |
      | ALTER              |
      | AND                |
      | ANY                |
      | ARRAY              |
      | AS                 |
      | ASC                |
      | ASCENDING          |
      | ASSIGN             |
      | AT                 |
      | AUTH               |
      | BINDINGS           |
      | BOOL               |
      | BOOLEAN            |
      | BOOSTED            |
      | BOTH               |
      | BREAK              |
      | BUILT              |
      | BY                 |
      | CALL               |
      | CASCADE            |
      | CASE               |
      | CHANGE             |
      | CIDR               |
      | COLLECT            |
      | COMMAND            |
      | COMMANDS           |
      | COMPOSITE          |
      | CONCURRENT         |
      | CONSTRAINT         |
      | CONSTRAINTS        |
      | CONTAINS           |
      | CONTINUE           |
      | COPY               |
      | COUNT              |
      | CREATE             |
      | CSV                |
      | CURRENT            |
      | DATA               |
      | DATABASE           |
      | DATABASES          |
      | DATE               |
      | DATETIME           |
      | DBMS               |
      | DEALLOCATE         |
      | DEFAULT            |
      | DEFINED            |
      | DELETE             |
      | DENY               |
      | DESC               |
      | DESCENDING         |
      | DESTROY            |
      | DETACH             |
      | DIFFERENT          |
      | DISTINCT           |
      | DRIVER             |
      | DROP               |
      | DRYRUN             |
      | DUMP               |
      | DURATION           |
      | EACH               |
      | EDGE               |
      | ELEMENT            |
      | ELEMENTS           |
      | ELSE               |
      | ENABLE             |
      | ENCRYPTED          |
      | END                |
      | ENDS               |
      | ERROR              |
      | EXECUTABLE         |
      | EXECUTE            |
      | EXIST              |
      | EXISTENCE          |
      | EXISTS             |
      | FAIL               |
      | FALSE              |
      | FIELDTERMINATOR    |
      | FINISH             |
      | FLOAT              |
      | FOREACH            |
      | FOR                |
      | FROM               |
      | FULLTEXT           |
      | FUNCTION           |
      | FUNCTIONS          |
      | GRANT              |
      | GRAPH              |
      | GRAPHS             |
      | GROUP              |
      | GROUPS             |
      | HEADERS            |
      | HOME               |
      | ID                 |
      | IF                 |
      | IMMUTABLE          |
      | IMPERSONATE        |
      | IN                 |
      | INDEX              |
      | INDEXES            |
      | INF                |
      | INFINITY           |
      | INSERT             |
      | INT                |
      | INTEGER            |
      | IS                 |
      | JOIN               |
      | KEY                |
      | LABEL              |
      | LABELS             |
      | LEADING            |
      | LIMITROWS          |
      | LIST               |
      | LOAD               |
      | LOCAL              |
      | LOOKUP             |
      | MATCH              |
      | MANAGEMENT         |
      | MAP                |
      | MERGE              |
      | NAME               |
      | NAMES              |
      | NAN                |
      | NEW                |
      | NFC                |
      | NFD                |
      | NFKC               |
      | NFKD               |
      | NODE               |
      | NODETACH           |
      | NODES              |
      | NONE               |
      | NORMALIZE          |
      | NORMALIZED         |
      | NOT                |
      | NOTHING            |
      | NOWAIT             |
      | NULL               |
      | OF                 |
      | OFFSET             |
      | ON                 |
      | ONLY               |
      | OPTIONAL           |
      | OPTIONS            |
      | OPTION             |
      | OR                 |
      | ORDER              |
      | PASSWORD           |
      | PASSWORDS          |
      | PATH               |
      | PATHS              |
      | PLAINTEXT          |
      | POINT              |
      | POPULATED          |
      | PRIMARY            |
      | PRIMARIES          |
      | PRIVILEGE          |
      | PRIVILEGES         |
      | PROCEDURE          |
      | PROCEDURES         |
      | PROPERTIES         |
      | PROPERTY           |
      | PROVIDER           |
      | PROVIDERS          |
      | RANGE              |
      | READ               |
      | REALLOCATE         |
      | REDUCE             |
      | REL                |
      | RELATIONSHIP       |
      | RELATIONSHIPS      |
      | REMOVE             |
      | RENAME             |
      | REPEATABLE         |
      | REPLACE            |
      | REPORT             |
      | REQUIRE            |
      | REQUIRED           |
      | RESTRICT           |
      | RETURN             |
      | REVOKE             |
      | ROLE               |
      | ROLES              |
      | ROW                |
      | ROWS               |
      | SCAN               |
      | SECONDARY          |
      | SECONDARIES        |
      | SEC                |
      | SECOND             |
      | SECONDS            |
      | SEEK               |
      | SERVER             |
      | SERVERS            |
      | SET                |
      | SETTING            |
      | SETTINGS           |
      | SHORTEST           |
      | SHORTEST_PATH      |
      | SHOW               |
      | SIGNED             |
      | SINGLE             |
      | SKIPROWS           |
      | START              |
      | STARTS             |
      | STATUS             |
      | STOP               |
      | VARCHAR            |
      | STRING             |
      | SUPPORTED          |
      | SUSPENDED          |
      | TARGET             |
      | TERMINATE          |
      | TEXT               |
      | THEN               |
      | TIME               |
      | TIMESTAMP          |
      | TIMEZONE           |
      | TO                 |
      | TOPOLOGY           |
      | TRAILING           |
      | TRANSACTION        |
      | TRANSACTIONS       |
      | TRAVERSE           |
      | TRIM               |
      | TRUE               |
      | TYPE               |
      | TYPED              |
      | TYPES              |
      | UNION              |
      | UNIQUE             |
      | UNIQUENESS         |
      | UNWIND             |
      | URL                |
      | USE                |
      | USER               |
      | USERS              |
      | USING              |
      | VALUE              |
      | VECTOR             |
      | VERTEX             |
      | WAIT               |
      | WHEN               |
      | WHERE              |
      | WITH               |
      | WITHOUT            |
      | WRITE              |
      | XOR                |
      | YIELD              |
      | ZONE               |
      | ZONED              |

  Scenario Outline: Keywords allowed as labels in patterns in MATCH #Example: <keyword>
    Given an empty graph
    And having executed:
      """
      CREATE (:<keyword>)-[:<keyword>]->(:<keyword>)
      """
    When executing query:
      """
      MATCH (n1:<keyword>)
      MATCH (n2 IS <keyword>)
      MATCH (n1)-[r1:<keyword>]->(n2)
      MATCH (n1)-[r2:<keyword>]-(n2)
      MATCH (n2)<-[r3:<keyword>]-(n1)
      MATCH (n1)-[r4 IS <keyword>]->(n2)
      MATCH (n1)-[r5 IS <keyword>]-(n2)
      MATCH (n2)-[r6 IS <keyword>]-(n1)
      RETURN n1, n2, r1, r2, r3, r4, r5, r6
      """
    Then the result should be, in any order:
      | n1           | n2           | r1           | r2           | r3           | r4           | r5           | r6           |
      | (:<keyword>) | (:<keyword>) | [:<keyword>] | [:<keyword>] | [:<keyword>] | [:<keyword>] | [:<keyword>] | [:<keyword>] |
    And no side effects

    Examples:
      | keyword            |
      | ACCESS             |
      | ACTIVE             |
      | ADMIN              |
      | ADMINISTRATOR      |
      | ALIAS              |
      | ALIASES            |
      | ALL_SHORTEST_PATHS |
      | ALL                |
      | ALTER              |
      | AND                |
      | ANY                |
      | ARRAY              |
      | AS                 |
      | ASC                |
      | ASCENDING          |
      | ASSIGN             |
      | AT                 |
      | AUTH               |
      | BINDINGS           |
      | BOOL               |
      | BOOLEAN            |
      | BOOSTED            |
      | BOTH               |
      | BREAK              |
      | BUILT              |
      | BY                 |
      | CALL               |
      | CASCADE            |
      | CASE               |
      | CHANGE             |
      | CIDR               |
      | COLLECT            |
      | COMMAND            |
      | COMMANDS           |
      | COMPOSITE          |
      | CONCURRENT         |
      | CONSTRAINT         |
      | CONSTRAINTS        |
      | CONTAINS           |
      | CONTINUE           |
      | COPY               |
      | COUNT              |
      | CREATE             |
      | CSV                |
      | CURRENT            |
      | DATA               |
      | DATABASE           |
      | DATABASES          |
      | DATE               |
      | DATETIME           |
      | DBMS               |
      | DEALLOCATE         |
      | DEFAULT            |
      | DEFINED            |
      | DELETE             |
      | DENY               |
      | DESC               |
      | DESCENDING         |
      | DESTROY            |
      | DETACH             |
      | DIFFERENT          |
      | DISTINCT           |
      | DRIVER             |
      | DROP               |
      | DRYRUN             |
      | DUMP               |
      | DURATION           |
      | EACH               |
      | EDGE               |
      | ELEMENT            |
      | ELEMENTS           |
      | ELSE               |
      | ENABLE             |
      | ENCRYPTED          |
      | END                |
      | ENDS               |
      | ERROR              |
      | EXECUTABLE         |
      | EXECUTE            |
      | EXIST              |
      | EXISTENCE          |
      | EXISTS             |
      | FAIL               |
      | FALSE              |
      | FIELDTERMINATOR    |
      | FINISH             |
      | FLOAT              |
      | FOREACH            |
      | FOR                |
      | FROM               |
      | FULLTEXT           |
      | FUNCTION           |
      | FUNCTIONS          |
      | GRANT              |
      | GRAPH              |
      | GRAPHS             |
      | GROUP              |
      | GROUPS             |
      | HEADERS            |
      | HOME               |
      | ID                 |
      | IF                 |
      | IMMUTABLE          |
      | IMPERSONATE        |
      | IN                 |
      | INDEX              |
      | INDEXES            |
      | INF                |
      | INFINITY           |
      | INSERT             |
      | INT                |
      | INTEGER            |
      | IS                 |
      | JOIN               |
      | KEY                |
      | LABEL              |
      | LABELS             |
      | LEADING            |
      | LIMITROWS          |
      | LIST               |
      | LOAD               |
      | LOCAL              |
      | LOOKUP             |
      | MATCH              |
      | MANAGEMENT         |
      | MAP                |
      | MERGE              |
      | NAME               |
      | NAMES              |
      | NAN                |
      | NEW                |
      | NFC                |
      | NFD                |
      | NFKC               |
      | NFKD               |
      | NODE               |
      | NODETACH           |
      | NODES              |
      | NONE               |
      | NORMALIZE          |
      | NORMALIZED         |
      | NOT                |
      | NOTHING            |
      | NOWAIT             |
      | NULL               |
      | OF                 |
      | OFFSET             |
      | ON                 |
      | ONLY               |
      | OPTIONAL           |
      | OPTIONS            |
      | OPTION             |
      | OR                 |
      | ORDER              |
      | PASSWORD           |
      | PASSWORDS          |
      | PATH               |
      | PATHS              |
      | PLAINTEXT          |
      | POINT              |
      | POPULATED          |
      | PRIMARY            |
      | PRIMARIES          |
      | PRIVILEGE          |
      | PRIVILEGES         |
      | PROCEDURE          |
      | PROCEDURES         |
      | PROPERTIES         |
      | PROPERTY           |
      | PROVIDER           |
      | PROVIDERS          |
      | RANGE              |
      | READ               |
      | REALLOCATE         |
      | REDUCE             |
      | REL                |
      | RELATIONSHIP       |
      | RELATIONSHIPS      |
      | REMOVE             |
      | RENAME             |
      | REPEATABLE         |
      | REPLACE            |
      | REPORT             |
      | REQUIRE            |
      | REQUIRED           |
      | RESTRICT           |
      | RETURN             |
      | REVOKE             |
      | ROLE               |
      | ROLES              |
      | ROW                |
      | ROWS               |
      | SCAN               |
      | SECONDARY          |
      | SECONDARIES        |
      | SEC                |
      | SECOND             |
      | SECONDS            |
      | SEEK               |
      | SERVER             |
      | SERVERS            |
      | SET                |
      | SETTING            |
      | SETTINGS           |
      | SHORTEST           |
      | SHORTEST_PATH      |
      | SHOW               |
      | SIGNED             |
      | SINGLE             |
      | SKIPROWS           |
      | START              |
      | STARTS             |
      | STATUS             |
      | STOP               |
      | VARCHAR            |
      | STRING             |
      | SUPPORTED          |
      | SUSPENDED          |
      | TARGET             |
      | TERMINATE          |
      | TEXT               |
      | THEN               |
      | TIME               |
      | TIMESTAMP          |
      | TIMEZONE           |
      | TO                 |
      | TOPOLOGY           |
      | TRAILING           |
      | TRANSACTION        |
      | TRANSACTIONS       |
      | TRAVERSE           |
      | TRIM               |
      | TRUE               |
      | TYPE               |
      | TYPED              |
      | TYPES              |
      | UNION              |
      | UNIQUE             |
      | UNIQUENESS         |
      | UNWIND             |
      | URL                |
      | USE                |
      | USER               |
      | USERS              |
      | USING              |
      | VALUE              |
      | VECTOR             |
      | VERTEX             |
      | WAIT               |
      | WHEN               |
      | WHERE              |
      | WITH               |
      | WITHOUT            |
      | WRITE              |
      | XOR                |
      | YIELD              |
      | ZONE               |
      | ZONED              |

  Scenario Outline: Keywords allowed as labels in node patterns in SET and REMOVE #Example: <keyword>
    Given an empty graph
    And having executed:
      """
      CREATE (:A {p: 1}),
         (:A {p: 2}),
         (:<keyword>:A {p: 3}),
         (:<keyword>:A {p: 4})
      """
    When executing query:
      """
      MATCH (n1 {p: 1})
      MATCH (n2 {p: 2})
      MATCH (n3 {p: 3})
      MATCH (n4 {p: 4})
      SET n1:<keyword>
      SET n2 IS <keyword>
      REMOVE n3:<keyword>
      REMOVE n4 IS <keyword>
      RETURN n1, n2, n3, n4
      """
    Then the result should be, in any order:
      | n1                    | n2                    | n3          | n4          |
      | (:<keyword>:A {p: 1}) | (:<keyword>:A {p: 2}) | (:A {p: 3}) | (:A {p: 4}) |
    And no side effects

    Examples:
      | keyword            |
      | ACCESS             |
      | ACTIVE             |
      | ADMIN              |
      | ADMINISTRATOR      |
      | ALIAS              |
      | ALIASES            |
      | ALL_SHORTEST_PATHS |
      | ALL                |
      | ALTER              |
      | AND                |
      | ANY                |
      | ARRAY              |
      | AS                 |
      | ASC                |
      | ASCENDING          |
      | ASSIGN             |
      | AT                 |
      | AUTH               |
      | BINDINGS           |
      | BOOL               |
      | BOOLEAN            |
      | BOOSTED            |
      | BOTH               |
      | BREAK              |
      | BUILT              |
      | BY                 |
      | CALL               |
      | CASCADE            |
      | CASE               |
      | CHANGE             |
      | CIDR               |
      | COLLECT            |
      | COMMAND            |
      | COMMANDS           |
      | COMPOSITE          |
      | CONCURRENT         |
      | CONSTRAINT         |
      | CONSTRAINTS        |
      | CONTAINS           |
      | CONTINUE           |
      | COPY               |
      | COUNT              |
      | CREATE             |
      | CSV                |
      | CURRENT            |
      | DATA               |
      | DATABASE           |
      | DATABASES          |
      | DATE               |
      | DATETIME           |
      | DBMS               |
      | DEALLOCATE         |
      | DEFAULT            |
      | DEFINED            |
      | DELETE             |
      | DENY               |
      | DESC               |
      | DESCENDING         |
      | DESTROY            |
      | DETACH             |
      | DIFFERENT          |
      | DISTINCT           |
      | DRIVER             |
      | DROP               |
      | DRYRUN             |
      | DUMP               |
      | DURATION           |
      | EACH               |
      | EDGE               |
      | ELEMENT            |
      | ELEMENTS           |
      | ELSE               |
      | ENABLE             |
      | ENCRYPTED          |
      | END                |
      | ENDS               |
      | ERROR              |
      | EXECUTABLE         |
      | EXECUTE            |
      | EXIST              |
      | EXISTENCE          |
      | EXISTS             |
      | FAIL               |
      | FALSE              |
      | FIELDTERMINATOR    |
      | FINISH             |
      | FLOAT              |
      | FOREACH            |
      | FOR                |
      | FROM               |
      | FULLTEXT           |
      | FUNCTION           |
      | FUNCTIONS          |
      | GRANT              |
      | GRAPH              |
      | GRAPHS             |
      | GROUP              |
      | GROUPS             |
      | HEADERS            |
      | HOME               |
      | ID                 |
      | IF                 |
      | IMMUTABLE          |
      | IMPERSONATE        |
      | IN                 |
      | INDEX              |
      | INDEXES            |
      | INF                |
      | INFINITY           |
      | INSERT             |
      | INT                |
      | INTEGER            |
      | IS                 |
      | JOIN               |
      | KEY                |
      | LABEL              |
      | LABELS             |
      | LEADING            |
      | LIMITROWS          |
      | LIST               |
      | LOAD               |
      | LOCAL              |
      | LOOKUP             |
      | MATCH              |
      | MANAGEMENT         |
      | MAP                |
      | MERGE              |
      | NAME               |
      | NAMES              |
      | NAN                |
      | NEW                |
      | NFC                |
      | NFD                |
      | NFKC               |
      | NFKD               |
      | NODE               |
      | NODETACH           |
      | NODES              |
      | NONE               |
      | NORMALIZE          |
      | NORMALIZED         |
      | NOT                |
      | NOTHING            |
      | NOWAIT             |
      | NULL               |
      | OF                 |
      | OFFSET             |
      | ON                 |
      | ONLY               |
      | OPTIONAL           |
      | OPTIONS            |
      | OPTION             |
      | OR                 |
      | ORDER              |
      | PASSWORD           |
      | PASSWORDS          |
      | PATH               |
      | PATHS              |
      | PLAINTEXT          |
      | POINT              |
      | POPULATED          |
      | PRIMARY            |
      | PRIMARIES          |
      | PRIVILEGE          |
      | PRIVILEGES         |
      | PROCEDURE          |
      | PROCEDURES         |
      | PROPERTIES         |
      | PROPERTY           |
      | PROVIDER           |
      | PROVIDERS          |
      | RANGE              |
      | READ               |
      | REALLOCATE         |
      | REDUCE             |
      | REL                |
      | RELATIONSHIP       |
      | RELATIONSHIPS      |
      | REMOVE             |
      | RENAME             |
      | REPEATABLE         |
      | REPLACE            |
      | REPORT             |
      | REQUIRE            |
      | REQUIRED           |
      | RESTRICT           |
      | RETURN             |
      | REVOKE             |
      | ROLE               |
      | ROLES              |
      | ROW                |
      | ROWS               |
      | SCAN               |
      | SECONDARY          |
      | SECONDARIES        |
      | SEC                |
      | SECOND             |
      | SECONDS            |
      | SEEK               |
      | SERVER             |
      | SERVERS            |
      | SET                |
      | SETTING            |
      | SETTINGS           |
      | SHORTEST           |
      | SHORTEST_PATH      |
      | SHOW               |
      | SIGNED             |
      | SINGLE             |
      | SKIPROWS           |
      | START              |
      | STARTS             |
      | STATUS             |
      | STOP               |
      | VARCHAR            |
      | STRING             |
      | SUPPORTED          |
      | SUSPENDED          |
      | TARGET             |
      | TERMINATE          |
      | TEXT               |
      | THEN               |
      | TIME               |
      | TIMESTAMP          |
      | TIMEZONE           |
      | TO                 |
      | TOPOLOGY           |
      | TRAILING           |
      | TRANSACTION        |
      | TRANSACTIONS       |
      | TRAVERSE           |
      | TRIM               |
      | TRUE               |
      | TYPE               |
      | TYPED              |
      | TYPES              |
      | UNION              |
      | UNIQUE             |
      | UNIQUENESS         |
      | UNWIND             |
      | URL                |
      | USE                |
      | USER               |
      | USERS              |
      | USING              |
      | VALUE              |
      | VECTOR             |
      | VERTEX             |
      | WAIT               |
      | WHEN               |
      | WHERE              |
      | WITH               |
      | WITHOUT            |
      | WRITE              |
      | XOR                |
      | YIELD              |
      | ZONE               |
      | ZONED              |

  Scenario Outline: Keywords in IS predicates allowed as labels in node patterns in MATCH #Example: IS <isPredicate>
    Given an empty graph
    And having executed:
      """
      CREATE (:<keyword> {p: 5}),
         (:FOO {p: 5}),
         (:FOO),
         (:FOO:<keyword>),
         (:<keyword>:FOO {p: "abc"}),
         (:FOO:<keyword>:BAR {p: "abc"})
      """
    When executing query:
      """
      MATCH (n1 IS <keyword> WHERE n1.p IS <isPredicate> <extraLabelPred>)
      WITH n1 ORDER BY n1
      WITH COLLECT(n1) AS n1s
      MATCH (n2:`<keyword>`<extraLabelEx>) WHERE n2.p IS <isPredicate>
      WITH n1s, n2 ORDER BY n2
      WITH n1s, COLLECT(n2) AS n2s
      MATCH (n3) WHERE "<keyword>" IN labels(n3) <extraLabelInPred> AND n3.p IS <isPredicate>
      WITH n1s, n2s, n3 ORDER BY n3
      WITH n1s, n2s, COLLECT(n3) AS n3s
      RETURN n1s = n2s = n3s AS result
      """
    Then the result should be, in any order:
      | result |
      | true   |
    And no side effects

    Examples:
      | keyword    | isPredicate     | extraLabelPred | extraLabelEx | extraLabelInPred        |
      | NOT        | NOT NULL        |                |              |                         |
      | NULL       | NULL            |                |              |                         |
      | TYPED      | TYPED INT       |                |              |                         |
      | NORMALIZED | NORMALIZED      |                |              |                         |
      | NFC        | NFC NORMALIZED  |                |              |                         |
      | NFD        | NFD NORMALIZED  |                |              |                         |
      | NFKC       | NFKC NORMALIZED |                |              |                         |
      | NFKD       | NFKD NORMALIZED |                |              |                         |
      | NOT        | NOT NULL        | AND n1 IS FOO  | &`FOO`       | AND "FOO" IN labels(n3) |
      | NULL       | NULL            | AND n1 IS FOO  | &`FOO`       | AND "FOO" IN labels(n3) |
      | TYPED      | TYPED INT       | AND n1 IS FOO  | &`FOO`       | AND "FOO" IN labels(n3) |
      | NORMALIZED | NORMALIZED      | AND n1 IS FOO  | &`FOO`       | AND "FOO" IN labels(n3) |
      | NFC        | NFC NORMALIZED  | AND n1 IS FOO  | &`FOO`       | AND "FOO" IN labels(n3) |
      | NFD        | NFD NORMALIZED  | AND n1 IS FOO  | &`FOO`       | AND "FOO" IN labels(n3) |
      | NFKC       | NFKC NORMALIZED | AND n1 IS FOO  | &`FOO`       | AND "FOO" IN labels(n3) |
      | NFKD       | NFKD NORMALIZED | AND n1 IS FOO  | &`FOO`       | AND "FOO" IN labels(n3) |

  Scenario Outline: Non-keywords in IS predicates in node patterns in MATCH
    Given an empty graph
    And having executed:
      """
      CREATE (:<nonKeyword> {p: 5}),
         (:X {p: 5}),
         (:X),
         (:X:<nonKeyword>),
         (:<nonKeyword>:Y {p: "abc"}),
         (:X:<nonKeyword>:Y {p: "abc"})
      """
    When executing query:
      """
      MATCH (n1 IS <nonKeyword>)
      WITH n1 ORDER BY n1
      WITH COLLECT(n1) AS n1s
      MATCH (n2 WHERE n2 IS <nonKeyword>)
      WITH n1s, n2 ORDER BY n2
      WITH n1s, COLLECT(n2) AS n2s
      MATCH (n3) WHERE "<nonKeyword>" IN labels(n3)
      WITH n1s, n2s, n3 ORDER BY n3
      WITH n1s, n2s, COLLECT(n3) AS n3s
      RETURN n1s = n2s = n3s AS result
      """
    Then the result should be, in any order:
      | result |
      | true   |
    And no side effects

    Examples:
      | nonKeyword |
      | FOO        |
      | BARFOO     |

  Scenario Outline: Keywords in IS predicates allowed as labels in relationship patterns in MATCH #Example: IS <predicate>
    Given an empty graph
    And having executed:
      """
      CREATE (n1), (n2)
      CREATE (n1)-[:<keyword> {p: 5}]->(n2),
         (n1)-[:FOO {p: 5}]->(n2),
         (n1)-[:<keyword>]->(n2),
         (n1)-[:FOO]->(n2),
         (n1)-[:<keyword> {p: "abc"}]->(n2),
         (n1)-[:FOO {p: "abc"}]->(n2)
      """
    When executing query:
      """
      MATCH ()-[r1 IS <keyword> WHERE r1.p IS <predicate>]->()
      WITH r1 ORDER BY r1
      WITH COLLECT(r1) AS r1s
      MATCH ()-[r2:`<keyword>`]->() WHERE r2.p IS <predicate>
      WITH r1s, r2 ORDER BY r2
      WITH r1s, COLLECT(r2) AS r2s
      MATCH ()-[r3]->() WHERE type(r3) = "<keyword>" AND r3.p IS <predicate>
      WITH r1s, r2s, r3 ORDER BY r3
      WITH r1s, r2s, COLLECT(r3) AS r3s
      RETURN r1s = r2s = r3s AS result
      """
    Then the result should be, in any order:
      | result |
      | true   |
    And no side effects

    Examples:
      | keyword    | predicate       |
      | NOT        | NOT NULL        |
      | NULL       | NULL            |
      | TYPED      | TYPED INT       |
      | NORMALIZED | NORMALIZED      |
      | NFC        | NFC NORMALIZED  |
      | NFD        | NFD NORMALIZED  |
      | NFKC       | NFKC NORMALIZED |
      | NFKD       | NFKD NORMALIZED |

  Scenario Outline: Non-keywords in IS predicates in relationship patterns in MATCH
    Given an empty graph
    And having executed:
      """
      CREATE (n1), (n2)
      CREATE (n1)-[:<nonKeyword> {p: 5}]->(n2),
         (n1)-[:R {p: 5}]->(n2),
         (n1)-[:<nonKeyword>]->(n2),
         (n1)-[:R]->(n2),
         (n1)-[:<nonKeyword> {p: "abc"}]->(n2),
         (n1)-[:R {p: "abc"}]->(n2)
      """
    When executing query:
      """
      MATCH ()-[r1 IS <nonKeyword>]->()
      WITH r1 ORDER BY r1
      WITH COLLECT(r1) AS r1s
      MATCH ()-[r2 WHERE r2 IS <nonKeyword>]->()
      WITH r1s, r2 ORDER BY r2
      WITH r1s, COLLECT(r2) AS r2s
      MATCH ()-[r3]->() WHERE type(r3) = "<nonKeyword>"
      WITH r1s, r2s, r3 ORDER BY r3
      WITH r1s, r2s, COLLECT(r3) AS r3s
      RETURN r1s = r2s = r3s AS result
      """
    Then the result should be, in any order:
      | result |
      | true   |
    And no side effects

    Examples:
      | nonKeyword |
      | FOO        |
      | BARFOO     |

  Scenario Outline: Simple case with keyword-base comparison operators have keyword interpretation #Example: <exampleName>
    Given an empty graph
    When executing query:
      """
      WITH <testValue> AS testValue, <varValue> AS <varName>
      WITH
        CASE testValue WHEN <part2Pred> THEN "A" ELSE "B" END AS test,
        CASE WHEN testValue = (<identifierInterpr>) THEN "A" ELSE "B" END AS identifierInterpretation,
        CASE WHEN testValue <keywordInterpr> THEN "A" ELSE "B" END AS keywordInterpretation
      RETURN
        test = keywordInterpretation AS isKeywordInterpretation,
        test <> identifierInterpretation AS isNotIdentifierInterpretation
      """
    Then the result should be, in any order:
      | isKeywordInterpretation | isNotIdentifierInterpretation |
      | true                    | true                          |
    And no side effects

    Examples:
      | testValue | varName    | varValue | part2Pred    | identifierInterpr | keywordInterpr | exampleName |
      | 1         | `IN`       | [3,"a"]  | IN [1]       | `IN`[1]           | IN ([1])       | IN          |
      | 2         | `CONTAINS` | 1        | CONTAINS + 1 | `CONTAINS` + 1    | CONTAINS (+1)  | CONTAINS +  |
      | 2         | `CONTAINS` | 3        | CONTAINS - 1 | `CONTAINS` - 1    | CONTAINS (-1)  | CONTAINS -  |
      | true      | `IS`       | 1        | IS :: INT    | `IS` IS TYPED INT | IS TYPED INT   | IS ::       |