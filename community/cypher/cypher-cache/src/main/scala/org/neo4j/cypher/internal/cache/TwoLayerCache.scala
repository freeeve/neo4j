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
package org.neo4j.cypher.internal.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Policy
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import com.github.benmanes.caffeine.cache.stats.CacheStats

import java.lang
import java.util
import java.util.Collections
import java.util.Map
import java.util.concurrent.ConcurrentMap
import java.util.function
import java.util.function.BiFunction

class TwoLayerCache[K, V](val primary: Cache[K, V], val secondary: Cache[K, V]) extends Cache[K, V] {

  override def getIfPresent(key: K): V = {
    val p = primary.getIfPresent(key)
    if (p != null) p
    else secondary.getIfPresent(key)
  }

  override def get(key: K, mappingFunction: function.Function[_ >: K, _ <: V]): V = {
    val p = primary.get(key, mappingFunction)
    if (p != null) p
    else secondary.getIfPresent(key)
  }

  override def getAllPresent(keys: lang.Iterable[_ <: K]): util.Map[K, V] = {
    throw new UnsupportedOperationException()
  }

  override def getAll(
    keys: lang.Iterable[_ <: K],
    mappingFunction: function.Function[_ >: util.Set[_ <: K], _ <: util.Map[_ <: K, _ <: V]]
  ): util.Map[K, V] = {
    throw new UnsupportedOperationException()
  }

  override def put(key: K, value: V): Unit = primary.put(key, value)

  override def putAll(map: util.Map[_ <: K, _ <: V]): Unit = primary.putAll(map)

  override def invalidate(key: K): Unit = {
    primary.invalidate(key)
    secondary.invalidate(key)
  }

  override def invalidateAll(keys: lang.Iterable[_ <: K]): Unit = {
    primary.invalidateAll(keys)
    secondary.invalidateAll(keys)
  }

  override def invalidateAll(): Unit = {
    primary.invalidateAll()
    secondary.invalidateAll()
  }

  override def estimatedSize(): Long = primary.estimatedSize() + secondary.estimatedSize()

  override def stats(): CacheStats = primary.stats().plus(secondary.stats())

  // Warning! Breaks contract of asMap, returns a snapshot of the current cache (with few exceptions)!
  override def asMap(): ConcurrentMap[K, V] = new ConcurrentMap[K, V]() {

    // Lazy to avoid expensive copy when not needed
    private lazy val snapshot = {
      val map = util.HashMap.newHashMap[K, V](math.min(estimatedSize(), Int.MaxValue).toInt)
      map.putAll(secondary.asMap())
      map.putAll(primary.asMap())
      Collections.unmodifiableMap(map)
    }

    override def size(): Int = snapshot.size()

    override def isEmpty: Boolean = snapshot.isEmpty

    override def containsKey(key: Any): Boolean = snapshot.containsKey(key)

    override def containsValue(value: Any): Boolean = snapshot.containsValue(value)

    override def get(key: Any): V = snapshot.get(key)

    override def put(key: K, value: V): V = throw new UnsupportedOperationException()

    override def remove(key: Any): V = throw new UnsupportedOperationException()

    override def putAll(m: util.Map[_ <: K, _ <: V]): Unit = throw new UnsupportedOperationException()

    override def clear(): Unit = throw new UnsupportedOperationException()

    override def keySet(): util.Set[K] = snapshot.keySet()

    override def values(): util.Collection[V] = snapshot.values()

    override def entrySet(): util.Set[Map.Entry[K, V]] = snapshot.entrySet()

    override def equals(obj: Any): Boolean = snapshot.equals(obj)

    override def hashCode(): Int = snapshot.hashCode()

    override def computeIfAbsent(key: K, mappingFunction: function.Function[_ >: K, _ <: V]): V =
      throw new UnsupportedOperationException()

    override def computeIfPresent(key: K, remappingFunction: BiFunction[_ >: K, _ >: V, _ <: V]): V =
      throw new UnsupportedOperationException()

    override def compute(key: K, remappingFunction: BiFunction[_ >: K, _ >: V, _ <: V]): V =
      throw new UnsupportedOperationException()

    override def merge(key: K, value: V, remappingFunction: BiFunction[_ >: V, _ >: V, _ <: V]): V =
      throw new UnsupportedOperationException()

    override def replaceAll(function: BiFunction[_ >: K, _ >: V, _ <: V]): Unit =
      throw new UnsupportedOperationException()

    override def remove(key: Any, value: Any): Boolean = throw new UnsupportedOperationException()

    override def putIfAbsent(key: K, value: V): V = throw new UnsupportedOperationException()

    // Note, this is called from the query cache (at the time of writing).
    override def replace(key: K, oldValue: V, newValue: V): Boolean = {
      primary.asMap().replace(key, oldValue, newValue) || secondary.asMap().replace(key, oldValue, newValue)
    }

    override def replace(key: K, value: V): V = throw new UnsupportedOperationException()
  }

  override def cleanUp(): Unit = {
    primary.cleanUp()
    secondary.cleanUp()
  }

  override def policy(): Policy[K, V] = {
    primary.policy()
  }

}

object TwoLayerCache {

  def evictionListener[K, V](receiver: Cache[K, V]): RemovalListener[K, V] =
    (key: K, value: V, cause: RemovalCause) => {
      if (cause.wasEvicted()) {
        receiver.put(key, value)
      }
    }
}
