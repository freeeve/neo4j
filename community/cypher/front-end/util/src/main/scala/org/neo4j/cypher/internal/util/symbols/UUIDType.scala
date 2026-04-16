/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.util.symbols

import org.neo4j.cypher.internal.util.InputPosition

case class UUIDType(isNullable: Boolean)(val position: InputPosition) extends CypherType {
  override val parentType: CypherType = CTAny
  override val toClassString: String = "UUID"
  override val toCypherTypeString: String = "UUID"
  override def sortOrder: Int = CypherTypeOrder.UUID.id

  override def couldBeStoredInProperty: Boolean = true

  override def withIsNullable(isNullable: Boolean): UUIDType = this.copy(isNullable = isNullable)(position)

  override def withPosition(newPosition: InputPosition): UUIDType = this.copy()(position = newPosition)
}
