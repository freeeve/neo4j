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
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import com.github.benmanes.caffeine.cache.Ticker
import org.neo4j.cypher.internal.cache.CaffeineCacheFactory.CacheConf
import org.neo4j.cypher.internal.cache.CaffeineCacheFactory.newCache
import org.neo4j.cypher.internal.cache.SharedExecutorBasedCaffeineCacheFactory.BackingCache
import org.neo4j.cypher.internal.cache.SharedExecutorBasedCaffeineCacheFactory.InternalListeners
import org.neo4j.cypher.internal.cache.SharedExecutorBasedCaffeineCacheFactory.SizeEstimation
import org.neo4j.util.VisibleForTesting

import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import scala.collection.concurrent.TrieMap

object CaffeineCacheFactory {

  case class CacheConf[K <: AnyRef, V <: AnyRef](
    executor: Executor,
    size: CacheSize,
    evictionListener: Option[RemovalListener[K, V]] = None,
    removalListener: Option[RemovalListener[K, V]] = None,
    softValues: Boolean = false,
    ttlAfterWriteMs: Option[Long] = None,
    ticker: Option[Ticker] = None
  ) {

    def withType[K2 <: AnyRef, V2 <: AnyRef](
      eviction: RemovalListener[K2, V2],
      removal: RemovalListener[K2, V2]
    ): CacheConf[K2, V2] = {
      CacheConf(executor, size, Some(eviction), Some(removal), softValues, ttlAfterWriteMs, ticker)
    }
  }

  /**
   * Please note that this constructor creates a cache with a synchronous executor. Caffeine would normally perform
   * a number of background operations such as expiring entries and compacting the cache which it would ideally perform
   * on a pool of background threads. It is recommended that you use [[org.neo4j.scheduler.MonitoredJobExecutor]] for
   * this as it would give users observability into the thread pool and your cache, as well as improve cache
   * performance. Instead, use this constructor when all you need is a Map with a size limit and evictions.
   */
  def newSynchronousCache[K <: AnyRef, V <: AnyRef](size: CacheSize): Cache[K, V] = {
    val currentThreadExecutor = new Executor {
      def execute(command: Runnable): Unit = command.run()
    }
    newCache(CacheConf[K, V](currentThreadExecutor, size))
  }

  def newCache[K <: AnyRef, V <: AnyRef](conf: CacheConf[K, V]): Cache[K, V] =
    conf.size.withSize[K, V, Cache[K, V]] { size =>
      var builder = Caffeine.newBuilder().asInstanceOf[Caffeine[K, V]]
        .executor(conf.executor)
        .maximumSize(size)
      builder = conf.evictionListener.fold(builder)(listener => builder.evictionListener(listener))
      builder = conf.removalListener.fold(builder)(listener => builder.removalListener(listener))
      builder = if (conf.softValues) builder.softValues() else builder
      builder = conf.ttlAfterWriteMs.fold(builder)(ttlMs => builder.expireAfterWrite(ttlMs, TimeUnit.MILLISECONDS))
      builder = conf.ticker.fold(builder)(ticker => builder.ticker(ticker))
      builder.build[K, V]()
    }
}

trait CacheFactory {
  def resolveCacheKind(kind: String): CaffeineCacheFactory
}

trait CaffeineCacheFactory extends CacheFactory {
  protected def executor: Executor
  private[cache] def create[K <: AnyRef, V <: AnyRef](conf: CacheConf[K, V]): Cache[K, V]

  final def createCache[K <: AnyRef, V <: AnyRef](size: CacheSize): Cache[K, V] = create(CacheConf(executor, size))

  final def createCache[K <: AnyRef, V <: AnyRef](size: CacheSize, eviction: RemovalListener[K, V]): Cache[K, V] =
    create(CacheConf(executor, size, Some(eviction)))

  final def createCache[K <: AnyRef, V <: AnyRef](ticker: Ticker, ttlAfterWrite: Long, size: CacheSize): Cache[K, V] =
    create(CacheConf(executor, size, ttlAfterWriteMs = Some(ttlAfterWrite), ticker = Some(ticker)))

  def createWithSoftBackingCache[K <: AnyRef, V <: AnyRef](
    primarySize: CacheSize,
    secondarySize: CacheSize,
    evictionListener: RemovalListener[K, V]
  ): Cache[K, V]
}

trait CacheTracerRepository {
  def tracerForCacheKind(kind: String): CacheTracer[?]
}

class ExecutorBasedCaffeineCacheFactory(override protected val executor: Executor) extends CaffeineCacheFactory {

  override private[cache] def create[K <: AnyRef, V <: AnyRef](conf: CacheConf[K, V]): Cache[K, V] = {
    require(conf.removalListener.isEmpty) // Shared cache needs changes to support an additional removal listener.
    newCache(conf)
  }

  override def createWithSoftBackingCache[K <: AnyRef, V <: AnyRef](
    strongSize: CacheSize,
    softSize: CacheSize,
    evictionListener: RemovalListener[K, V]
  ): Cache[K, V] = {
    val secondary = newCache(CacheConf(executor, softSize, Some(evictionListener), softValues = true))
    val primary = newCache(CacheConf(executor, strongSize, Some(new TwoLayerCache.EvictionListener(secondary))))
    new TwoLayerCache[K, V](primary, secondary)
  }

  override def resolveCacheKind(kind: String): CaffeineCacheFactory = this
}

final class SharedExecutorBasedCaffeineCacheFactory(
  executor: Executor,
  val cacheTracerRepository: CacheTracerRepository
) extends CacheFactory { self =>

  private val backingCacheByKind = new scala.collection.concurrent.TrieMap[String, BackingCache[?, ?]]()

  override def resolveCacheKind(kind: String): SharedCacheFactoryForKind = new SharedCacheFactoryForKind(kind)

  def close(databaseId: Int): Unit = backingCacheByKind.values.foreach(_.close(databaseId))

  private def tracer[K](kind: String): CacheTracer[K] = {
    cacheTracerRepository.tracerForCacheKind(kind).asInstanceOf[CacheTracer[K]]
  }

  @VisibleForTesting
  def getCacheSizeOf(kind: String): Long = backingCacheByKind.get(kind) match {
    case Some(cache) =>
      cache.cache.cleanUp()
      cache.cache.estimatedSize()
    case None => 0L
  }

  @VisibleForTesting
  def cleanUpCache(kind: String): Unit = backingCacheByKind.get(kind).foreach(_.cache.cleanUp())

  @VisibleForTesting
  def invalidateAllEntries(kind: String): Unit = backingCacheByKind.get(kind).foreach(_.cache.invalidateAll())

  @VisibleForTesting
  private[cache] def backingCache(kind: String): Option[BackingCache[?, ?]] = backingCacheByKind.get(kind)

  // Note, estimated size is less correct in this implementation when using ttl and soft cache.
  // See SharedCachePropertyTest.
  class SharedCacheFactoryForKind(kind: String) extends CaffeineCacheFactory {
    override protected def executor: Executor = self.executor

    override def resolveCacheKind(kind: String): SharedCacheFactoryForKind = this

    override private[cache] def create[K <: AnyRef, V <: AnyRef](conf: CacheConf[K, V]): SharedCacheContainer[K, V] = {
      val id = SharedCacheContainerIdGen.getNewId
      val backingCache = backingCacheByKind.getOrElseUpdate(kind, newBackingCache(conf))
        .asInstanceOf[BackingCache[K, V]]

      val sizeEstimation = SizeEstimation()
      backingCache.listeners.registerDb(id, sizeEstimation, conf.evictionListener)

      SharedCacheContainer(backingCache.cache, id, tracer[K](kind), self, sizeEstimation)
    }

    override def createWithSoftBackingCache[K <: AnyRef, V <: AnyRef](
      primarySize: CacheSize,
      secondarySize: CacheSize,
      evictionListener: RemovalListener[K, V]
    ): SharedCacheContainer[K, V] = {
      val id = SharedCacheContainerIdGen.getNewId
      val backingCache = backingCacheByKind.getOrElseUpdate(kind, newSoftValuesBackingCache(primarySize, secondarySize))
        .asInstanceOf[BackingCache[K, V]]

      val sizeEstimation = SizeEstimation()
      backingCache.listeners.registerDb(id, sizeEstimation, Some(evictionListener))

      SharedCacheContainer(backingCache.cache, id, tracer[K](kind), self, sizeEstimation)
    }

    private def newSoftValuesBackingCache[K <: AnyRef, V <: AnyRef](
      primarySize: CacheSize,
      secondarySize: CacheSize
    ): BackingCache[K, V] = {
      val secondary = newBackingCache[K, V](CacheConf(executor, secondarySize, softValues = true))
      val primaryEviction = new TwoLayerCache.EvictionListener(secondary.cache) {
        private val listeners = secondary.listeners
        override def onPut(key: (Int, K), oldValue: V, value: V): Unit =
          if (oldValue == null) listeners.onPut(key._1)
      }
      val primary = newCache[(Int, K), V](CacheConf(
        executor = executor,
        size = primarySize,
        evictionListener = Some(primaryEviction),
        removalListener = Some(secondary.listeners.removalListener)
      ))
      val backingCache = new TwoLayerCache[(Int, K), V](primary, secondary.cache)
      BackingCache(kind, backingCache, secondary.listeners)
    }

    private def newBackingCache[K <: AnyRef, V <: AnyRef](conf: CacheConf[K, V]): BackingCache[K, V] = {
      require(conf.removalListener.isEmpty) // Needs changes below to support an additional removal listener.
      val listeners = new InternalListeners[K, V](tracer[K](kind))
      BackingCache(kind, newCache(conf.withType(listeners.evictionListener, listeners.removalListener)), listeners)
    }
  }
}

object SharedExecutorBasedCaffeineCacheFactory {

  private case class DbListeners[K, V](eviction: Option[RemovalListener[K, V]], removal: SizeEstimation)

  abstract private class DbListener[K, V](
    private val dbListeners: TrieMap[Int, DbListeners[K, V]]
  ) extends RemovalListener[(Int, K), V] {

    final override def onRemoval(key: (Int, K), value: V, cause: RemovalCause): Unit = dbListeners.get(key._1) match {
      case Some(listeners) => onRemoval(listeners, key._2, value, cause)
      case None            =>
    }

    def onRemoval(listeners: DbListeners[K, V], key: K, value: V, cause: RemovalCause): Unit
  }

  final class InternalListeners[K, V](tracer: CacheTracer[K]) {
    private val dbListeners: TrieMap[Int, DbListeners[K, V]] = new scala.collection.concurrent.TrieMap()

    val evictionListener: RemovalListener[(Int, K), V] = new DbListener(dbListeners) {
      override def onRemoval(listeners: DbListeners[K, V], key: K, value: V, cause: RemovalCause): Unit = {
        listeners.eviction.foreach(listener => listener.onRemoval(key, value, cause))
        tracer.discard(key, "")
      }
    }

    val removalListener: RemovalListener[(Int, K), V] = new DbListener(dbListeners) {
      override def onRemoval(listeners: DbListeners[K, V], key: K, value: V, cause: RemovalCause): Unit = {
        if (cause != RemovalCause.REPLACED) {
          listeners.removal.onRemoval()
        }
      }
    }

    def onPut(id: Int): Unit = dbListeners.get(id).foreach(_.removal.onPut())

    def registerDb(id: Int, size: SizeEstimation, external: Option[RemovalListener[K, V]]): Unit = {
      dbListeners.put(id, DbListeners(external, size))
    }

    def unregisterListeners(id: Int): Unit = dbListeners.remove(id)
  }

  case class BackingCache[K, V](kind: String, cache: Cache[(Int, K), V], listeners: InternalListeners[K, V]) {
    def close(id: Int): Unit = listeners.unregisterListeners(id)
  }

  /**
   * This class provides O(1) size estimation of caches backed by a shared cache.
   * The estimated size is used for certain metrics,
   * which became very slow in the previous implementation (to the point of making metrics totally un-responsive)
   * in dbmses with many databases and high cache size.
   */
  case class SizeEstimation() {
    private val sizeEstimateCounter = new AtomicLong(0)
    def onRemoval(): Unit = sizeEstimateCounter.decrementAndGet()
    def onPut(): Unit = sizeEstimateCounter.incrementAndGet()
    def sizeEstimate(): Long = math.max(0L, sizeEstimateCounter.get())
  }
}

object SharedCacheContainerIdGen {
  private val id = new AtomicInteger(0)

  def getNewId: Int = id.getAndIncrement()
}
