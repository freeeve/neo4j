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
package org.neo4j.cypher.internal.ir.helpers

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

import scala.collection.mutable
import scala.util.DynamicVariable

trait CachedFunction {
  def cacheSize: Long
}

/**
 * CachedFunction is not thread-safe.
 */
object CachedFunction {

  private def defaultCaffeineConfig: Caffeine[AnyRef, AnyRef] = {
    Caffeine.newBuilder().maximumSize(100)
  }

  sealed trait CacheFactory {
    def makeCache[A, B](name: String): Cache[A, B]
  }

  private val FACTORY: DynamicVariable[CacheFactory] = new DynamicVariable[CacheFactory](CacheFactory.Default)

  /**
   * Wrap a block of code creating and using CachedFunction to print cache usage stats at the end.
   * E.g. in an acceptance test:
   * {{{
   * val result = CachedFunction.withScopedStatsRecordingEnabled {
   *   executeSingle(query)
   * }
   * }}}
   */
  def withScopedStatsRecordingEnabled[T](thunk: => T): T = {
    val factory = new CacheFactory.WithStatsRecording()
    try FACTORY.withValue(factory) { thunk }
    finally factory.printStats()
  }

  def apply[A, B](f: A => B)(implicit meta: sourcecode.Enclosing): (A => B) with CachedFunction = {
    new (A => B) with CachedFunction {
      private val cache: Cache[A, B] = FACTORY.value.makeCache(meta.value)

      override def cacheSize: Long = {
        cache.cleanUp()
        cache.estimatedSize()
      }

      def apply(input: A): B = {
        /*
         * For concurrency reasons, it's better to use cache.get(), which utilizes proper locking.
         * However, because `f` may update other mappings of this cache, cache.get() can not be used.
         * This means that `CachedFunction` is not thread-safe.
         */
        val cachedValue = cache.getIfPresent(input)
        if (cachedValue != null) {
          cachedValue
        } else {
          val newValue = f(input)
          cache.put(input, newValue)
          newValue
        }
      }
    }
  }

  def apply[A, B, C](f: (A, B) => C)(implicit meta: sourcecode.Enclosing): ((A, B) => C) with CachedFunction = {
    val tupledCachedFunction = apply(f.tupled)
    val untupledCachedFunction = Function.untupled(tupledCachedFunction)
    new ((A, B) => C) with CachedFunction {
      override def apply(v1: A, v2: B): C = untupledCachedFunction(v1, v2)
      override def cacheSize: Long = tupledCachedFunction.cacheSize
    }
  }

  def apply[A, B, C, D](f: (A, B, C) => D)(implicit
    meta: sourcecode.Enclosing): ((A, B, C) => D) with CachedFunction = {
    val tupledCachedFunction = apply(f.tupled)
    val untupledCachedFunction = Function.untupled(tupledCachedFunction)
    new ((A, B, C) => D) with CachedFunction {
      override def apply(a: A, b: B, c: C): D = untupledCachedFunction(a, b, c)
      override def cacheSize: Long = tupledCachedFunction.cacheSize
    }
  }

  def apply[A, B, C, D, E](f: (A, B, C, D) => E)(implicit
    meta: sourcecode.Enclosing): ((A, B, C, D) => E) with CachedFunction = {
    val tupledCachedFunction = apply(f.tupled)
    val untupledCachedFunction = Function.untupled(tupledCachedFunction)
    new ((A, B, C, D) => E) with CachedFunction {
      override def apply(v1: A, v2: B, v3: C, v4: D): E = untupledCachedFunction(v1, v2, v3, v4)
      override def cacheSize: Long = tupledCachedFunction.cacheSize
    }
  }

  def apply[A, B, C, D, E, F](f: (A, B, C, D, E) => F)(implicit
    meta: sourcecode.Enclosing): ((A, B, C, D, E) => F) with CachedFunction = {
    val tupledCachedFunction = apply(f.tupled)
    val untupledCachedFunction = Function.untupled(tupledCachedFunction)
    new ((A, B, C, D, E) => F) with CachedFunction {
      override def apply(v1: A, v2: B, v3: C, v4: D, v5: E): F = untupledCachedFunction(v1, v2, v3, v4, v5)
      override def cacheSize: Long = tupledCachedFunction.cacheSize
    }
  }

  def apply[A, B, C, D, E, F, G](f: (A, B, C, D, E, F) => G)(implicit
    meta: sourcecode.Enclosing): ((A, B, C, D, E, F) => G) with CachedFunction = {
    {
      val tupledCachedFunction = apply(f.tupled)
      val untupledCachedFunction = untupled(tupledCachedFunction)
      new ((A, B, C, D, E, F) => G) with CachedFunction {
        override def apply(v1: A, v2: B, v3: C, v4: D, v5: E, v6: F): G = untupledCachedFunction(v1, v2, v3, v4, v5, v6)
        override def cacheSize: Long = tupledCachedFunction.cacheSize
      }
    }
  }

  def apply[A, B, C, D, E, F, G, H](f: (A, B, C, D, E, F, G) => H)(implicit
    meta: sourcecode.Enclosing): ((A, B, C, D, E, F, G) => H) with CachedFunction = {
    {
      val tupledCachedFunction = apply(f.tupled)
      val untupledCachedFunction = untupled(tupledCachedFunction)
      new ((A, B, C, D, E, F, G) => H) with CachedFunction {
        override def apply(v1: A, v2: B, v3: C, v4: D, v5: E, v6: F, v7: G): H =
          untupledCachedFunction(v1, v2, v3, v4, v5, v6, v7)

        override def cacheSize: Long = tupledCachedFunction.cacheSize
      }
    }
  }

  def apply[A, B, C, D, E, F, G, H, I](f: (A, B, C, D, E, F, G, H) => I)(implicit meta: sourcecode.Enclosing)
    : ((A, B, C, D, E, F, G, H) => I) with CachedFunction = {
    {
      val tupledCachedFunction = apply(f.tupled)
      val untupledCachedFunction = untupled(tupledCachedFunction)
      new ((A, B, C, D, E, F, G, H) => I) with CachedFunction {
        override def apply(v1: A, v2: B, v3: C, v4: D, v5: E, v6: F, v7: G, v8: H): I =
          untupledCachedFunction(v1, v2, v3, v4, v5, v6, v7, v8)

        override def cacheSize: Long = tupledCachedFunction.cacheSize
      }
    }
  }

  /** Un-tupling for functions of arity 6. This transforms a function taking
   * a 6-tuple of arguments into a function of arity 6 which takes each argument separately.
   */
  def untupled[a1, a2, a3, a4, a5, a6, b](f: ((a1, a2, a3, a4, a5, a6)) => b): (a1, a2, a3, a4, a5, a6) => b = {
    (x1, x2, x3, x4, x5, x6) => f(Tuple6(x1, x2, x3, x4, x5, x6))
  }

  /** Un-tupling for functions of arity 7. This transforms a function taking
   * a 7-tuple of arguments into a function of arity 7 which takes each argument separately.
   */
  def untupled[a1, a2, a3, a4, a5, a6, a7, b](f: ((a1, a2, a3, a4, a5, a6, a7)) => b)
    : (a1, a2, a3, a4, a5, a6, a7) => b = {
    (x1, x2, x3, x4, x5, x6, x7) => f(Tuple7(x1, x2, x3, x4, x5, x6, x7))
  }

  /** Un-tupling for functions of arity 8. This transforms a function taking
   * a 8-tuple of arguments into a function of arity 8 which takes each argument separately.
   */
  def untupled[a1, a2, a3, a4, a5, a6, a7, a8, b](f: ((a1, a2, a3, a4, a5, a6, a7, a8)) => b)
    : (a1, a2, a3, a4, a5, a6, a7, a8) => b = {
    (x1, x2, x3, x4, x5, x6, x7, x8) => f(Tuple8(x1, x2, x3, x4, x5, x6, x7, x8))
  }

  /** Allows passing [[value]] into [[CachedFunction]] while only using [[cacheKey]] for cache lookup.
   *
   * Because [[value]] is passed via the second argument list, it is excluded from generated equals() and hashCode() methods.
   */
  final case class CacheKey[Key, Value](cacheKey: Key)(val value: Value)

  object CacheKey {

    def computeFrom[Key, Value](value: Value)(f: Value => Key): CacheKey[Key, Value] = {
      CacheKey(f(value))(value)
    }
  }

  private object CacheFactory {

    case object Default extends CacheFactory {

      override def makeCache[A, B](name: String): Cache[A, B] = {
        defaultCaffeineConfig.build().asInstanceOf[Cache[A, B]]
      }
    }

    class WithStatsRecording() extends CacheFactory {

      private val caches: mutable.ArrayBuffer[(Cache[_, _], String)] = mutable.ArrayBuffer.empty

      override def makeCache[A, B](name: String): Cache[A, B] = {
        val cache = defaultCaffeineConfig
          .recordStats()
          .build()
          .asInstanceOf[Cache[A, B]]

        caches += ((cache, name))
        cache
      }

      def printStats(): Unit = {
        val table = new Table()
        table.addRow(" name", "hit%", "hits", "misses", "cache size", " raw caffeine stats")

        caches
          .sortBy { case (cache, _) => (cache.stats().hitRate(), -cache.stats().missCount()) }
          .foreach { case (cache, name) =>
            val stats = cache.stats()
            table.addRow(
              name,
              (100 * stats.hitRate()).toInt.toString,
              stats.hitCount().toString,
              stats.missCount().toString,
              cache.estimatedSize().toString,
              stats.toString
            )
          }
        table.printTable()
      }

      private class Table() {
        private val segments: mutable.Map[(Int, Int, Int), String] = mutable.Map.empty
        private val rowHeights: mutable.Map[Int, Int] = mutable.Map.empty
        private val columnWidths: mutable.Map[Int, Int] = mutable.Map.empty
        private var rows = 0
        private var columns = 0
        private val MAX_WIDTH: Int = 60

        def addRow(cells: String*): Unit = {
          val row = rows
          for ((cell, col) <- cells.zipWithIndex) {
            val width = math.min(MAX_WIDTH, cell.length)
            val wrappedCell = wordwrap(cell)
            val height = wrappedCell.linesIterator.length
            columnWidths.update(col, math.max(columnWidths.getOrElse(col, 0), width))
            rowHeights.update(row, math.max(rowHeights.getOrElse(row, 0), height))
            for ((segment, line) <- wrappedCell.linesIterator.zipWithIndex) {
              segments.update((row, col, line), segment)
            }
          }
          rows += 1
          columns = math.max(columns, cells.length)
        }

        def printTable(): Unit = {
          val separatorSegments = (0 until columns).map(col => "─" * columnWidths(col))
          val separator = separatorSegments.mkString("├", "┼", "┤")
          val header = separatorSegments.mkString("┌", "┬", "┐")
          val footer = separatorSegments.mkString("└", "┴", "┘")

          println(header)
          for (row <- 0 until rows) {
            for (line <- 0 until rowHeights(row)) {
              val lineSegments = for (col <- 0 until columns) yield {
                segments.getOrElse((row, col, line), "")
                  .padTo(columnWidths(col), ' ')
              }
              val lineStr = lineSegments.mkString("│", "│", "│")
              println(lineStr)
            }
            if (row != rows - 1) {
              println(separator)
            }
          }
          println(footer)
        }

        private def wordwrap(s: String): String = s.grouped(MAX_WIDTH).mkString("\n")
      }
    }
  }
}
