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
import org.neo4j.util.VisibleForTesting

import java.lang
import java.util
import java.util.Collections
import java.util.Map
import java.util.concurrent.ConcurrentMap
import java.util.function
import java.util.function.BiConsumer
import java.util.function.BiFunction

class TwoLayerCache[K, V](primary: Cache[K, V], secondary: Cache[K, V]) extends Cache[K, V] {

  override def getIfPresent(key: K): V = {
    val p = primary.getIfPresent(key)
    if (p != null) p
    else secondary.getIfPresent(key)
  }

  override def get(key: K, mappingFunction: function.Function[? >: K, ? <: V]): V = {
    val p = primary.get(key, mappingFunction)
    if (p != null) {
      secondary.invalidate(key)
      p
    } else {
      secondary.getIfPresent(key)
    }
  }

  override def getAllPresent(keys: lang.Iterable[? <: K]): util.Map[K, V] = throw unsupported

  override def getAll(
    keys: lang.Iterable[? <: K],
    mappingFunction: function.Function[? >: util.Set[? <: K], ? <: util.Map[? <: K, ? <: V]]
  ): util.Map[K, V] = {
    throw unsupported
  }

  override def put(key: K, value: V): Unit = {
    secondary.invalidate(key)
    primary.put(key, value)
  }

  override def putAll(map: util.Map[? <: K, ? <: V]): Unit = primary.putAll(map)

  override def invalidate(key: K): Unit = {
    primary.invalidate(key)
    secondary.invalidate(key)
  }

  override def invalidateAll(keys: lang.Iterable[? <: K]): Unit = {
    primary.invalidateAll(keys)
    secondary.invalidateAll(keys)
  }

  override def invalidateAll(): Unit = {
    primary.invalidateAll()
    secondary.invalidateAll()
  }

  override def estimatedSize(): Long = primary.estimatedSize() + secondary.estimatedSize()

  override def stats(): CacheStats = primary.stats().plus(secondary.stats())

  // Warning! Breaks contract of asMap, this implementation returns a snapshot (with few exceptions)!
  override def asMap(): ConcurrentMap[K, V] = new ConcurrentMap[K, V]() {
    // Lazy to avoid expensive snapshotting when not needed
    private lazy val snapshot = {
      val map = util.HashMap.newHashMap[K, V](primary.asMap().size() + secondary.asMap().size())
      map.putAll(secondary.asMap())
      map.putAll(primary.asMap())
      Collections.unmodifiableMap(map)
    }

    // Methods that works as expected on the underlying map
    // ====================================================

    override def put(key: K, value: V): V = {
      secondary.invalidate(key)
      val oldValue = primary.asMap().put(key, value)
      oldValue
    }

    // Note, this is called from the query cache (at the time of writing).
    override def replace(key: K, oldValue: V, newValue: V): Boolean = {
      primary.asMap().replace(key, oldValue, newValue) || secondary.asMap().replace(key, oldValue, newValue)
    }

    override def forEach(action: BiConsumer[? >: K, ? >: V]): Unit = {
      val primaryMap = primary.asMap()
      primaryMap.forEach(action)
      val secondaryMap = secondary.asMap()
      secondaryMap.forEach((key, value) => if (!primaryMap.containsKey(key)) action.accept(key, value))
    }

    // Methods that relies on the snapshot
    // ===================================

    override def size(): Int = snapshot.size()
    override def isEmpty: Boolean = snapshot.isEmpty
    override def containsKey(key: Any): Boolean = snapshot.containsKey(key)
    override def containsValue(value: Any): Boolean = snapshot.containsValue(value)
    override def get(key: Any): V = snapshot.get(key)
    override def keySet(): util.Set[K] = snapshot.keySet()
    override def values(): util.Collection[V] = snapshot.values()
    override def entrySet(): util.Set[Map.Entry[K, V]] = snapshot.entrySet()
    override def equals(obj: Any): Boolean = snapshot.equals(obj)
    override def hashCode(): Int = snapshot.hashCode()

    // Unsupported methods
    // ===================

    override def remove(key: Any): V = throw unsupported
    override def putAll(m: util.Map[? <: K, ? <: V]): Unit = throw unsupported
    override def clear(): Unit = throw unsupported
    override def computeIfAbsent(key: K, f: function.Function[? >: K, ? <: V]): V = throw unsupported
    override def computeIfPresent(key: K, f: BiFunction[? >: K, ? >: V, ? <: V]): V = throw unsupported
    override def compute(key: K, f: BiFunction[? >: K, ? >: V, ? <: V]): V = throw unsupported
    override def merge(key: K, value: V, f: BiFunction[? >: V, ? >: V, ? <: V]): V = throw unsupported
    override def replaceAll(f: BiFunction[? >: K, ? >: V, ? <: V]): Unit = throw unsupported
    override def remove(key: Any, value: Any): Boolean = throw unsupported
    override def putIfAbsent(key: K, value: V): V = throw unsupported
    override def replace(key: K, value: V): V = throw unsupported
  }

  override def cleanUp(): Unit = {
    primary.cleanUp()
    secondary.cleanUp()
  }

  override def policy(): Policy[K, V] = {
    primary.policy()
  }

  private def unsupported: UnsupportedOperationException = new UnsupportedOperationException()

  @VisibleForTesting
  def getPrimary: Cache[K, V] = primary

  @VisibleForTesting
  def getSecondary: Cache[K, V] = secondary
}

object TwoLayerCache {

  class EvictionListener[K, V](receiver: Cache[K, V]) extends RemovalListener[K, V] {

    final override def onRemoval(key: K, value: V, cause: RemovalCause): Unit = {
      if (cause.wasEvicted()) {
        val old = receiver.asMap().put(key, value)
        onPut(key, old, value)
      }
    }

    def onPut(key: K, oldValue: V, value: V): Unit = {}
  }
}
