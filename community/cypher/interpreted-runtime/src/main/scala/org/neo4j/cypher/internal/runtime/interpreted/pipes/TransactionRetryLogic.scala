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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExponentialBackoffRetryLogic.DEFAULT_MAX_RETRY_TIME_NANOS
import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExponentialBackoffRetryLogic.ExponentialBackoffRetryState
import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExponentialBackoffRetryLogic.INITIAL_RETRY_DELAY_NANOS
import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExponentialBackoffRetryLogic.MAX_RETRY_DELAY_NANOS
import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExponentialBackoffRetryLogic.RETRY_DELAY_JITTER_FACTOR
import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExponentialBackoffRetryLogic.RETRY_DELAY_MULTIPLIER
import org.neo4j.cypher.internal.runtime.interpreted.pipes.TransactionRetryLogic.RetryState

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit.SECONDS

import scala.concurrent.duration.MILLISECONDS

/**
 * Cypher query inner transaction retry logic for CALL {} IN TRANSACTIONS ON ERROR RETRY.
 */
object TransactionRetryLogic {

  trait RetryState {
    def retryCount: Int
    def compareTo(other: RetryState): Int
  }
}

trait TransactionRetryLogic {
  def newRetryState(): RetryState
  def computeNextRetryState(state: RetryState, nextState: RetryState): Unit
  def shouldRetryAgain(state: RetryState): Boolean
  def nanosUntilRetry(state: RetryState): Long
}

/**
 * A stateful retry logic that implements exponential backoff with jitter.
 */
class ExponentialBackoffRetryLogic(
  maxRetryTimeNanos: Long = DEFAULT_MAX_RETRY_TIME_NANOS,
  initialRetryDelayNanos: Long = INITIAL_RETRY_DELAY_NANOS,
  multiplier: Double = RETRY_DELAY_MULTIPLIER,
  jitterFactor: Double = RETRY_DELAY_JITTER_FACTOR,
  maxRetryDelayNanos: Long = MAX_RETRY_DELAY_NANOS
) extends TransactionRetryLogic {

  require(maxRetryTimeNanos >= 0, s"maxRetryTimeNanos should be >= 0: $maxRetryTimeNanos")
  require(initialRetryDelayNanos >= 0, s"initialRetryDelayNanos should be >= 0: $initialRetryDelayNanos")
  require(multiplier >= 1.0, s"multiplier should be >= 1.0: $multiplier")
  require(jitterFactor >= 0 && jitterFactor < 1, s"jitterFactor must be in the range [0.0, 1.0]: $jitterFactor")
  require(maxRetryDelayNanos >= 0, s"maxRetryDelayNanos should be >= 0: $maxRetryDelayNanos")

  override def newRetryState(): ExponentialBackoffRetryState = {
    val initialDelay = (initialRetryDelayNanos.toDouble / multiplier).toLong
    new ExponentialBackoffRetryState(0, 0L, 0L, _retryDelayNanos = initialDelay, _retryDelayWithJitterNanos = 0L)
  }

  override def computeNextRetryState(_state: RetryState, _nextState: RetryState): Unit = {
    val state = _state.asInstanceOf[ExponentialBackoffRetryState]
    val nextState = _nextState.asInstanceOf[ExponentialBackoffRetryState]
    val retryDelayNanos = nextRetryDelayNanos(state._retryDelayNanos)
    val retryDelayWithJitterNanos = retryDelayWithJitter(retryDelayNanos)
    nextState._retryCount = state._retryCount + 1
    nextState._retryDelayNanos = retryDelayNanos
    nextState._retryDelayWithJitterNanos = retryDelayWithJitterNanos
    val t = System.nanoTime()
    if (nextState._retryCount == 1) {
      nextState._firstRetryTimestamp = t
    } else {
      nextState._firstRetryTimestamp = state._firstRetryTimestamp
    }
    nextState._retryTimestamp = t + retryDelayWithJitterNanos
  }

  override def shouldRetryAgain(_state: RetryState): Boolean = {
    val state = _state.asInstanceOf[ExponentialBackoffRetryState]
    state._retryTimestamp - state._firstRetryTimestamp < maxRetryTimeNanos
  }

  override def nanosUntilRetry(_state: RetryState): Long = {
    val state = _state.asInstanceOf[ExponentialBackoffRetryState]
    val ts = state._retryTimestamp
    val now = System.nanoTime()
    val nanosUntilRetry = ts - now
    nanosUntilRetry
  }

  private def nextRetryDelayNanos(lastRetryDelayNanos: Long): Long = {
    val delay = (lastRetryDelayNanos * multiplier).toLong
    Math.min(delay, maxRetryDelayNanos)
  }

  private def retryDelayWithJitter(delayNanos: Long): Long = {
    val jitter = (delayNanos * jitterFactor).toLong
    val min = delayNanos - jitter
    val max = delayNanos + jitter
    ThreadLocalRandom.current.nextLong(min, max + 1)
  }
}

object ExponentialBackoffRetryLogic {
  final val DEFAULT_MAX_RETRY_TIME_NANOS: Long = SECONDS.toNanos(30)
  final val INITIAL_RETRY_DELAY_NANOS = MILLISECONDS.toNanos(10)
  final val RETRY_DELAY_MULTIPLIER = 2.0
  final val RETRY_DELAY_JITTER_FACTOR = 0.2
  final val MAX_RETRY_DELAY_NANOS = Long.MaxValue / 2

  final class ExponentialBackoffRetryState(
    var _retryCount: Int,
    var _firstRetryTimestamp: Long,
    var _retryTimestamp: Long,
    var _retryDelayNanos: Long,
    var _retryDelayWithJitterNanos: Long
  ) extends RetryState {
    override def retryCount: Int = _retryCount

    override def compareTo(other: RetryState): Int = {
      val o = other.asInstanceOf[ExponentialBackoffRetryState]
      java.lang.Long.compare(_retryTimestamp, o._retryTimestamp)
    }

    override def toString: String = {
      s"ExponentialBackoffRetryState(retryCount=${_retryCount}, retryTimestamp=${_retryTimestamp}, " +
        s"retryDelayNanos=${_retryDelayNanos}, retryDelayWithJitterNanos=${_retryDelayWithJitterNanos})"
    }
  }
}
