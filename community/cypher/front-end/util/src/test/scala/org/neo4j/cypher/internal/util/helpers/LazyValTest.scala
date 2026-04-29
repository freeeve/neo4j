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
package org.neo4j.cypher.internal.util.helpers

import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class LazyValTest extends CypherFunSuite {

  test("should delay computation") {
    var called = false
    val lazyVal = LazyVal {
      called = true
      42
    }
    called shouldBe false
    lazyVal.value shouldBe 42
    called shouldBe true
  }

  test("should compute lazy value only once") {
    var called = 0
    val lazyVal = LazyVal {
      called += 1
      42
    }
    called shouldBe 0
    lazyVal.value shouldBe 42
    lazyVal.value shouldBe 42
    lazyVal.value shouldBe 42
    called shouldBe 1
  }

  test("should be possible to re-compute after an exception") {
    var called = 0
    val lazyVal = LazyVal {
      called += 1
      if (called == 1) throw new RuntimeException("boom")
      42
    }
    called shouldBe 0
    a[RuntimeException] shouldBe thrownBy {
      lazyVal.value
    }
    lazyVal.value shouldBe 42
    called shouldBe 2
  }
}
