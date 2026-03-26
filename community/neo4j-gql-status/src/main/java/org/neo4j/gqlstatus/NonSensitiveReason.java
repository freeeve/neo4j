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
package org.neo4j.gqlstatus;

/*
 * General rule: a status description parameter is considered sensitive if it might contain
 * customer data. This includes (but is not limited to):
 * - property values
 * - input data provided by the user
 * - full or substantial parts of Cypher queries
 * - Cypher parameter values
 * - free text msg/cause parameters depending on how those are used
 */
enum NonSensitiveReason {

    // Names or ids of databases/graphs/aliases/servers/transactions etc.
    TOPOLOGY,

    // Metadata, mainly connected to Bolt, like request message type, struct tag, routing policy etc.
    METADATA,

    // Names of config settings
    CONFIG_SETTING,

    // Names, methods, signatures etc. of procedures and functions
    // WARNING: input arguments to procedures or functions can contain customer data, so those should not be
    // included here unless they have explicitly been deemed non-sensitive (e.g. booleans or enum values)
    PROCEDURES_FUNCTIONS,

    // Non-sensitive parts of Cypher queries such as clauses, keywords and preparser options
    CYPHER_CONSTRUCT,

    // Variable names from the Cypher query, these are not considered literals and are not obfuscated when the
    // query is logged, so should be safe to include in errors as well
    CYPHER_VARIABLE,

    // Cypher or Java value types such as 'INTEGER NOT NULL' or 'String' and descriptions of types such as
    // 'a nullable type' or 'a list'
    VALUE_TYPE,

    // Name of temporal/spatial components, formats, crs etc., like minute, YYMMDD, cartesian
    // WARNING: temporal and spatial literals are considered sensitive, so those should not be included here
    TEMPORAL_SPATIAL,

    // Names and ids of labels, relationship types, property keys, indexes, constraints, nodes, graph types etc.
    // These are considered usage data and not customer data
    // This category also covers words like 'label', 'node' and 'relationship'
    SCHEMA,

    // Max values, counts and other similar number literals which are not customer data
    NON_SENSITIVE_NUMBER,

    // Fixed paramterized text, e.g. when one of two fixed sentences should be printed depending on some condition
    FIXED_TEXT
}
