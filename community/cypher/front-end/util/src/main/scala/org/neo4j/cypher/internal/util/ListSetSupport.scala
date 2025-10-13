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
package org.neo4j.cypher.internal.util

import org.neo4j.cypher.internal.util.collection.immutable.ListSet

object ListSetSupport {

  implicit class RichListSet[A](listSet: ListSet[A]) {

    /** Partitions this `ListSet`, grouping values into `ListSet`s according to some discriminator function,
     *  preserving the order in which the values are first encountered.
     *
     *  For example:
     *  {{{
     *   scala> val groups = ListSet("foo", "", "bar").sequentiallyGroupBy(_.length)
     *   groups: ListSet[(Int, ListSet[String])] = ListSet((3,ListSet(foo, bar)), (0,ListSet("")))
     *  }}}
     *
     *  Compare with the default `groupBy` implementation, note how the resulting `ListSet` starts with the second value, there is no guaranteed order:
     *  {{{
     *   scala> val groups = ListSet("foo", "", "bar").groupBy(_.length).to(ListSet)
     *   groups: ListSet[(Int, ListSet[String])] = ListSet((0,ListSet("")), (3,ListSet(foo, bar)))
     *  }}}
     *
     *  @param f     the discriminator function.
     *  @tparam K    the type of keys returned by the discriminator function.
     *  @return      A `ListSet` of tuples each containing a key `k = f(x)` and all the elements `x` of the sequence where `f(x)` is equal to `k`.
     */
    def sequentiallyGroupBy[K](f: A => K): ListSet[(K, ListSet[A])] =
      IterableHelper.sequentiallyGroupBy[A, K, ListSet, ListSet](listSet)(f)
  }
}
