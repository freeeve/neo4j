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
package org.neo4j.cypher.internal.compiler.planner.logical.idp

import org.neo4j.configuration.GraphDatabaseInternalSettings
import org.neo4j.cypher.internal.compiler.planner.logical.ProjectingSelector
import org.neo4j.cypher.internal.compiler.planner.logical.Selector
import org.neo4j.cypher.internal.compiler.planner.logical.SelectorHeuristic
import org.neo4j.cypher.internal.compiler.planner.logical.idp.IDPCache.SatisfiedExtraRequirements
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.collection.immutable.ListSet
import org.neo4j.exceptions.InternalException
import org.neo4j.time.Stopwatch

import java.util.concurrent.TimeUnit

import scala.collection.immutable.BitSet

trait IDPSolverMonitor {
  def startIteration(iteration: Int): Unit
  def endIteration(iteration: Int, depth: Int, tableSize: Int): Unit
  def foundPlanAfter(iterations: Int): Unit
}

trait ExtraRequirement[-Result] {
  def fulfils(result: Result): Boolean
}

object ExtraRequirement {
  // Logically, everything would fulfil an empty requirement.
  // `false` leads to the same result, though, and is cheaper,
  // since we do not need to keep two different buckets in the table,
  // one for all candidates and one for the fulfilling ones.
  val empty: ExtraRequirement[Any] = (_: Any) => false
}

/**
 * @param bestResult The best result overall. May or may not fulfill the extra requirement
 * @param bestSortedResult The best result that fulfills the extra order requirement. May be the same as bestResult.
 * @param bestExtraPropertiesResult The best result that fetches additional properties that may be used later in the plan. May be the same as the bestResult and bestSortedResult.
 */
case class BestResults[+Result](
  bestResult: Result,
  bestSortedResult: Option[Result],
  bestExtraPropertiesResult: Option[Result]
) {

  def map[B](f: Result => B): BestResults[B] =
    BestResults(f(bestResult), bestSortedResult.map(f), bestExtraPropertiesResult.map(f))

  /**
   * Returns all unique results.
   */
  def allResults: Iterable[Result] = Set(bestResult) ++ bestSortedResult ++ bestExtraPropertiesResult

  /**
   * Gets the bestResultFulfillingReq if present, otherwise gets bestResult
   */
  def result: Result = bestSortedResult.orElse(bestExtraPropertiesResult).getOrElse(bestResult)

  override def toString: String =
    s"""BestResults(
       |  bestResult =
       |    ${bestResult.toString.replace("\n", "\n    ")},
       |  bestSortedResult =
       |    ${bestSortedResult.map(_.toString.replace("\n", "\n    ")).getOrElse("None")},
       |  bestExtraPropertiesResult =
       |    ${bestExtraPropertiesResult.map(_.toString.replace("\n", "\n    ")).getOrElse("None")}
       |)""".stripMargin
}

/**
 * Based on the main loop of the IDP1 algorithm described in the paper
 *
 *   "Iterative Dynamic Programming: A New Class of Query Optimization Algorithms"
 *
 * written by Donald Kossmann and Konrad Stocker
 */
class IDPSolver[Solvable: IDPLoggable, Result, Context](
  generator: IDPSolverStep[Solvable, Result, Context], // generates candidates at each step
  projectingSelector: ProjectingSelector[Result], // pick best from a set of candidates
  registryFactory: () => IdRegistry[Solvable] = () => IdRegistry[Solvable], // maps from Set[S] to BitSet
  tableFactory: (IdRegistry[Solvable], Seed[Solvable, Result]) => IDPTable[Result] =
    (registry: IdRegistry[Solvable], seed: Seed[Solvable, Result]) => IDPTable(registry, seed),
  maxTableSize: Int, // limits computation effort, reducing result quality
  iterationDurationLimit: Long, // limits computation effort, reducing result quality
  extraOrderRequirement: ExtraRequirement[Result],
  extraPropertyRequirement: ExtraRequirement[Result],
  monitor: IDPSolverMonitor,
  stopWatchFactory: () => Stopwatch,
  cancellationChecker: CancellationChecker,
  idpLogger: IDPLogger = IDPLogger.NoLogging
) {

  def apply(seed: Seed[Solvable, Result], initialToDo: ListSet[Solvable], context: Context): BestResults[Result] = {
    idpLogger.markScope("IDP") {
      run(seed, initialToDo, context)
    }
  }

  /**
   * Run the IDP solver
   *
   * The Goal which is created from initialTodo will have the bits in the same order as initialTodo.
   */
  private def run(
    seed: Seed[Solvable, Result],
    initialToDo: ListSet[Solvable],
    context: Context
  ): BestResults[Result] = {
    val registry = registryFactory()
    var toDo = Goal(registry.registerAll(initialToDo))
    val table = tableFactory(registry, seed)

    def generateBestCandidates(maxBlockSize: Int): Int = {
      var largestFinishedIteration = 0
      var blockSize = 1
      var keepGoing = true
      val start = stopWatchFactory()
      val tableSizeBefore = table.size

      while (keepGoing && blockSize <= maxBlockSize) {
        var foundNoCandidate = true
        blockSize += 1
        val goals = toDo.subGoals(blockSize)
        while (keepGoing && goals.hasNext) {
          cancellationChecker.throwIfCancelled()

          val goal = goals.next()
          if (!table.contains(goal, sorted = false, extraProperties = false)) {
            val candidates: Iterable[Result] = generator(registry, goal, table, context).toVector
            val (extraPropertiesCandidates, extraOrderCandidates, baseCandidates) =
              classify(candidates.iterator, extraPropertyRequirement.fulfils, extraOrderRequirement.fulfils)
            val bestSortedCandidate = candidateSelector(
              s"best sorted plan for ${goal.bitSet}@${registry.explode(goal.bitSet)}"
            )(extraOrderCandidates)
            val bestPrefetchedPropertiesCandidate = candidateSelector(
              s"best plan with pre-fetched properties for ${goal.bitSet}@${registry.explode(goal.bitSet)}"
            )(extraPropertiesCandidates)

            // We don't want to compare just the ones that do not fulfil the requirement
            // in isolation, because it could be that the best overall candidate fulfils the requirement.
            // bestSortedCandidate or bestPrefetchedPropertiesCandidate has already been determined to be cheaper than any other extraCandidate,
            // therefore it is enough to cost estimate the bestSortedCandidate against all baseCandidates.
            candidateSelector(s"best overall plan for ${goal.bitSet}@${registry.explode(goal.bitSet)}")(
              baseCandidates ++ bestSortedCandidate ++ bestPrefetchedPropertiesCandidate
            ).foreach { candidate =>
              foundNoCandidate = false
              table.put(goal, sorted = false, hasExtraProperties = false, candidate)
            }
            // Also add the best candidate from all candidates that fulfil the requirement into the table
            // with `true`.
            bestSortedCandidate.foreach { candidate =>
              foundNoCandidate = false
              table.put(goal, sorted = true, hasExtraProperties = false, candidate)
            }

            bestPrefetchedPropertiesCandidate.foreach { candidate =>
              foundNoCandidate = false
              table.put(goal, sorted = false, hasExtraProperties = true, candidate)
            }

            keepGoing =
              if (blockSize == 2) {
                true
              } else if (table.size > maxTableSize) {
                logTableSizeLimitReached(start, table, tableSizeBefore, blockSize, maxBlockSize)
                false
              } else if (start.hasTimedOut(iterationDurationLimit, TimeUnit.MILLISECONDS)) {
                logTimeLimitReached(start, table, tableSizeBefore, blockSize, maxBlockSize)
                false
              } else {
                true
              }
          }
        }
        largestFinishedIteration = if (foundNoCandidate || goals.hasNext) largestFinishedIteration else blockSize
      }
      if (keepGoing) {
        logIterationFullyCompleted(start, table, tableSizeBefore, blockSize, maxBlockSize)
      }

      largestFinishedIteration
    }

    def findBestCandidateInBlock(blockSize: Int, iteration: Int): Goal = {
      // Find all candidates that solve the highest number of relationships, ignoring sorted plans.
      val blockCandidates: Iterable[(Goal, Result)] = table.unsortedPlansOfSize(blockSize).toVector
      // Select the best of those. These candidates solve different things.
      // The best of the candidates is likely to appear in larger plans, so it is a good idea to compact that one.
      val bestInBlock: Option[(Goal, Result)] =
        goalSelector(s"Best candidate for block size $blockSize (IDP iteration #$iteration)")(blockCandidates)
      val (goal, _) = bestInBlock.getOrElse {
        throw InternalException.foundNoSolutionForBlock(blockSize, blockCandidates.toString(), table.toString)
      }
      goal
    }

    def compactBlock(original: Goal): Unit = {
      logCompactionStart(registry, original)

      val newId = registry.compact(original.bitSet)
      val IDPCache.Results(result, sortedResult, extraPropertiesResult) = table(original)
      result.foreach { table.put(Goal(BitSet.empty + newId), sorted = false, hasExtraProperties = false, _) }
      sortedResult.foreach { table.put(Goal(BitSet.empty + newId), sorted = true, hasExtraProperties = false, _) }
      extraPropertiesResult.foreach {
        table.put(Goal(BitSet.empty + newId), sorted = false, hasExtraProperties = true, _)
      }
      toDo = Goal(toDo.bitSet -- original.bitSet + newId)
      table.removeAllTracesOf(original)

      logCompactionEnd(table, newId)
    }

    // actual algorithm

    var iterations = 0

    logStart(registry, table, toDo)

    while (toDo.size > 1) {
      iterations += 1
      idpLogger.log(s"Iteration $iterations")
      monitor.startIteration(iterations)
      val largestFinished = generateBestCandidates(toDo.size)
      if (largestFinished <= 0) {
        throw InternalException.foundNoPlanWithinConstraints(
          GraphDatabaseInternalSettings.cypher_idp_solver_table_threshold.name(),
          GraphDatabaseInternalSettings.cypher_idp_solver_duration_threshold.name()
        )
      }
      val bestGoal = findBestCandidateInBlock(largestFinished, iterations)
      monitor.endIteration(iterations, largestFinished, table.size)
      // Compaction is either done at the very end of the algorithm, or when we hit a table size or time limit.
      // In the latter case, the goal is the one with the best (unsorted) result.
      // In the view of compaction, it does not matter which goal we compact, but is important to keep both the sorted and unsorted
      // results of that goal.
      compactBlock(bestGoal)
    }
    monitor.foundPlanAfter(iterations)
    idpLogger.log(s"Done after $iterations iteration(s)")

    val plansWithResult = table.plans
      .map { case ((_, fulfilsReq), result) => (fulfilsReq, result) }

    val (plansFulfillingExtraProperties, sortedPlans, basePlans) = classify(
      plansWithResult,
      (planWithResult: (SatisfiedExtraRequirements, Result)) => planWithResult._1.hasExtraProperties,
      (planWithResult: (SatisfiedExtraRequirements, Result)) => planWithResult._1.sorted
    )

    BestResults(
      singleResult(basePlans),
      singleOrEmptyResult(sortedPlans),
      singleOrEmptyResult(plansFulfillingExtraProperties)
    )
  }

  private def candidateSelector(resolved: => String): Selector[Result] =
    projectingSelector.apply[Result](identity[Result], _, resolved)

  private def goalSelector(resolved: => String): Selector[(Goal, Result)] =
    projectingSelector.applyWithResolvedPerPlan[(Goal, Result)](
      // project the result
      _._2,
      _,
      resolved,
      _ => "",
      SelectorHeuristic.constant,
      planDescriptor = { case (goal, _) => Some(s"Goal: ${goal.bitSet}") }
    )

  private def singleResult(vector: Vector[(IDPCache.SatisfiedExtraRequirements, Result)]): Result = vector match {
    case Vector(t) => t._2
    case _ => throw InternalException.internalError(
        this.getClass.getSimpleName,
        "Expected a single plan to be left in the plan table"
      )
  }

  private def singleOrEmptyResult(vector: Vector[(IDPCache.SatisfiedExtraRequirements, Result)]): Option[Result] =
    vector match {
      case Vector()  => None
      case Vector(t) => Some(t._2)
      case _ =>
        throw InternalException.internalError(
          this.getClass.getSimpleName,
          "Expected a single plan that fulfils the requirements to be left in the plan table"
        )
    }

  /**
   * Classifies the iterableOnce by the input predicates into 3 groups.
   * group1: are all elements that satisfy predicate1
   * group2: are all elements that satisfy predicate2
   * group3: are all elements that satisfy neither predicate1 nor predicate2.
   *
   * group1 and group2 may have overlapping elements.
   * @param predicate1
   * @param predicate2
   * @return three lists based on the elements solving inner predicates.
   */
  private def classify[T](
    elementIterator: Iterator[T],
    predicate1: T => Boolean,
    predicate2: T => Boolean
  ): (Vector[T], Vector[T], Vector[T]) = {
    val predicate1Iterable, predicate2Iterable, noneMatchIterable = Vector.newBuilder[T]
    elementIterator.foreach { element =>
      val isPredicate1Accepted = predicate1.apply(element)
      val isPredicate2Accepted = predicate2.apply(element)
      if (!isPredicate1Accepted && !isPredicate2Accepted) {
        noneMatchIterable.addOne(element)
      }
      if (isPredicate1Accepted) {
        predicate1Iterable.addOne(element)
      }
      if (isPredicate2Accepted) {
        predicate2Iterable.addOne(element)
      }
    }

    (predicate1Iterable.result(), predicate2Iterable.result(), noneMatchIterable.result())
  }

  private def logStart(registry: IdRegistry[Solvable], table: IDPTable[Result], toDo: Goal): Unit = {
    idpLogger.log {
      val goalsSummaries = toDo
        .bitSet
        .toVector
        .sorted
        .flatMap(i => registry.lookup(i).map(i -> _))
        .map {
          case (idx, solvable) =>
            s"[$idx] ${IDPLoggable.summary(solvable)}"
        }

      s"Initial table size = ${table.size}\n" +
        s"Goals [${goalsSummaries.size}]: ${goalsSummaries.mkString("[\n  ", ",\n  ", "\n]")}"
    }
  }

  private def logCompactionStart(registry: IdRegistry[Solvable], original: Goal): Unit = {
    idpLogger.log {
      val originalGoalsSummaries = registry.explode(original.bitSet).map(IDPLoggable.summary(_))
      val originalGoalsBits = registry.explodedBitSet(original.bitSet)
      s"Compacting goal ${original.bitSet} (exploded = $originalGoalsBits) = ${originalGoalsSummaries.mkString("[\n  ", ",\n  ", "\n]")}"
    }
  }

  private def logCompactionEnd(table: IDPTable[Result], newId: Int): Unit = {
    idpLogger.log(s"New compacted goal id = $newId, compacted table size: ${table.size}")
  }

  private def logIterationFullyCompleted(
    start: Stopwatch,
    table: IDPTable[Result],
    tableSizeBefore: Int,
    blockSize: Int,
    maxBlockSize: Int
  ): Unit = {
    idpLogger.log {
      s"[✓] all done, ${formatIterationState(start, table, tableSizeBefore, blockSize, maxBlockSize)}"
    }
  }

  private def logTableSizeLimitReached(
    start: Stopwatch,
    table: IDPTable[Result],
    tableSizeBefore: Int,
    blockSize: Int,
    maxBlockSize: Int
  ): Unit = {
    logLimitReached("table size", start, table, tableSizeBefore, blockSize, maxBlockSize)
  }

  private def logTimeLimitReached(
    start: Stopwatch,
    table: IDPTable[Result],
    tableSizeBefore: Int,
    blockSize: Int,
    maxBlockSize: Int
  ): Unit = {
    logLimitReached("time", start, table, tableSizeBefore, blockSize, maxBlockSize)
  }

  private def logLimitReached(
    limitType: String,
    start: Stopwatch,
    table: IDPTable[Result],
    tableSizeBefore: Int,
    blockSize: Int,
    maxBlockSize: Int
  ): Unit = {
    idpLogger.log {
      s"[!] $limitType limit reached, ${formatIterationState(start, table, tableSizeBefore, blockSize, maxBlockSize)}"
    }
  }

  private def formatIterationState(
    start: Stopwatch,
    table: IDPTable[Result],
    tableSizeBefore: Int,
    blockSize: Int,
    maxBlockSize: Int
  ): String = {
    val elapsedTimeMs = start.elapsed(TimeUnit.MILLISECONDS)
    s"time(ms)=$elapsedTimeMs/$iterationDurationLimit, table=[${table.size}/$maxTableSize (+${table.size - tableSizeBefore})], blockSize=[$blockSize/$maxBlockSize]"
  }
}
