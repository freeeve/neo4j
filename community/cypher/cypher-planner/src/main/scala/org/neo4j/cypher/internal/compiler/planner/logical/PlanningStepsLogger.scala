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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.options.CypherDebugOptions

import scala.collection.mutable

trait PlanningStepsLogger {
  def log(msg: => String): this.type
  def startFunction(name: String, inputValue: LogicalPlan): this.type
  def stopFunction(outputValue: LogicalPlan): this.type
  def flushFunctionLog(): String
}

object PlanningStepsLogger {

  case object NoLogging extends PlanningStepsLogger {
    override def log(msg: => String): this.type = this

    override def startFunction(name: String, inputValue: LogicalPlan): this.type = this

    override def stopFunction(outputValue: LogicalPlan): this.type = this

    override def flushFunctionLog(): String = ""
  }

  def givenDebugOptions(debugOptions: CypherDebugOptions): PlanningStepsLogger =
    if (debugOptions.logPlanningSteps)
      PlanningStepsSystemOutLogger()
    else
      NoLogging
}

case class PlanningStepsSystemOutLogger() extends PlanningStepsLogger {

  case class FunctionCall(
    name: String,
    children: mutable.ListBuffer[FunctionCall] = mutable.ListBuffer()
  ) {
    var changedInput: Boolean = false

    def render: String = {
      val selfRender =
        if (children.isEmpty) {
          name
        } else {
          s"$name(${FunctionCall.renderSeveral(children)})"
        }
      if (changedInput) selfRender else s"[$selfRender]"
    }
  }

  object FunctionCall {

    def renderSeveral(calls: Iterable[FunctionCall]): String =
      calls.map(_.render).mkString(" -> ")
  }

  private val functionStack: mutable.Stack[(FunctionCall, LogicalPlan)] = mutable.Stack()
  private val functionCalls = mutable.ListBuffer[FunctionCall]()

  override def log(msg: => String): this.type = {
    println(msg)
    this
  }

  override def startFunction(name: String, inputValue: LogicalPlan): this.type = {
    val parentFunction =
      if (functionStack.isEmpty) {
        None
      } else {
        val parent = functionStack.top._1
        Some(parent)
      }
    val functionCall = FunctionCall(name)
    parentFunction.foreach(_.children.append(functionCall))
    functionStack.push((functionCall, inputValue))
    this
  }

  override def stopFunction(outputValue: LogicalPlan): this.type = {
    val (functionCall, inputValue) = functionStack.pop()
    val isChanged = inputValue != outputValue
    functionCall.changedInput = isChanged

    if (functionStack.isEmpty) {
      functionCalls.append(functionCall)
    }

    this
  }

  override def flushFunctionLog(): String = {
    val result = FunctionCall.renderSeveral(functionCalls)
    functionCalls.clear()
    result
  }
}
