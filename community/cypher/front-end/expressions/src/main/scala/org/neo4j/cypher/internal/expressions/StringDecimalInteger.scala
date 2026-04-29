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
package org.neo4j.cypher.internal.expressions

import org.neo4j.cypher.internal.util.helpers.LazyVal

import scala.util.matching.Regex

trait StringDecimalInteger {
  def stringVal: String
  def value: java.lang.Long = lazyValue.value
  private val lazyValue: LazyVal[java.lang.Long] = LazyVal(StringDecimalInteger.stringToLong(stringVal))
}

object StringDecimalInteger {
  final private val integerMatcher: Regex = """-?\d+((_\d+)?)*""".r

  def stringToLong(stringValue: String): java.lang.Long = {
    if (stringValue.contains("_") && integerMatcher.matches(stringValue)) {
      java.lang.Long.parseLong(stringValue.replace("_", ""))
    } else {
      java.lang.Long.parseLong(stringValue)
    }
  }
}
