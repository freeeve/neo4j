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
package org.neo4j.cypher.cucumber.value

import fastparse.CharIn
import fastparse.CharsWhile
import fastparse.CharsWhileIn
import fastparse.EagerOps
import fastparse.EagerOpsStr
import fastparse.End
import fastparse.IgnoreCase
import fastparse.P
import fastparse.Parsed
import fastparse.ParsingRun
import fastparse.Start
import fastparse.Whitespace
import fastparse.internal.Msgs
import org.neo4j.cypher.cucumber.value.ValueRepresentation.Connection
import org.neo4j.cypher.cucumber.value.ValueRepresentation.NoIdNode
import org.neo4j.cypher.cucumber.value.ValueRepresentation.NoIdPath
import org.neo4j.cypher.cucumber.value.ValueRepresentation.NoIdRel
import org.neo4j.values.storable.Values

import java.util.Collections

import scala.jdk.CollectionConverters.ListHasAsScala
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.jdk.CollectionConverters.SetHasAsJava

/**
 * Parses Cypher values from .feature files.
 */
object CypherCucumberValueParser {

  private var driverParameters = false;

  def parse(value: String, asDriverParameter: Boolean = false): AnyRef = {
    driverParameters = asDriverParameter
    fastparse.parse[AnyRef](value, statement(_), verboseFailures = true) match {
      case Parsed.Success(value, _) => value
      case failure: Parsed.Failure  => throw new IllegalArgumentException(s"Failed to parse $value: ${failure.msg}")
    }
  }

  implicit private val whitespace: Whitespace = LimitedWhiteSpace
  private def statement[X: P]: P[AnyRef] = Start ~ cypherValue ~ End

  private def cypherValue[X: P]: P[AnyRef] =
    string |
      float |
      integer |
      boolean |
      nullValue |
      node |
      relationship |
      path |
      list |
      map |
      nanValue |
      vector

  def escape[X: P] = P("\\" ~~ CharIn("'\\\\"))
  def stringChars[X: P] = P(CharsWhile(c => c != '\'' && c != '\\'))

  def string[X: P]: P[java.lang.String] = P("\'" ~~/ (stringChars | escape).repX.! ~~ "\'")
    .map(_.replace("\\'", "'").replace("\\\\", "\\"))
  private def integer[X: P]: P[java.lang.Long] = P("-".? ~~ digits).!.map(_.toLong)
  private def float[X: P]: P[java.lang.Double] = P("-".? ~ floatRepr).!.map(_.toDouble)
  private def boolean[X: P]: P[java.lang.Boolean] = P("true" | "false").!.map(_.toBoolean)
  private def nullValue[X: P]: P[AnyRef] = P("null").!.map(_ => null)
  private def list[X: P]: P[java.util.List[AnyRef]] = P("[" ~~ cypherValue.rep(sep = ",") ~~/ "]").map(_.asJava)

  private def map[X: P]: P[java.util.Map[String, AnyRef]] =
    P("{" ~~/ keyValue.rep(sep = ",") ~~/ "}").map(_.toMap.asJava)

  private def node[X: P]: P[NoIdNode] = P("(" ~~/ label.rep ~/ map.? ~/ ")")
    .map { case (labels, properties) => NoIdNode(labels.toSet.asJava, properties.getOrElse(Collections.emptyMap())) }

  private def relationship[X: P]: P[NoIdRel] = P("[" ~~ label ~/ map.? ~/ "]")
    .map { case (relType, props) => NoIdRel(relType, props.getOrElse(Collections.emptyMap())) }

  private def path[X: P]: P[NoIdPath] = P("<" ~~/ node ~~/ (outgoing | incoming).rep ~~/ ">").map {
    case (node, links) =>
      NoIdPath(node, links)
  }

  private def nanValue[X: P]: P[java.lang.Double] = P("NaN").!.map(_ => Double.NaN)

  private def vectorCoordinateType[X: P]: P[java.lang.String] = P(CharsWhileIn("A-Za-z0-9")).!

  private def vector[X: P]: P[AnyRef] = P(vectorCoordinateType ~/ list).map {
    case (vectorType: String, coordinates: java.util.List[AnyRef]) =>
      vectorType match {
        case "Int8Vector" =>
          val coordinateArray: Array[Byte] = coordinates.asScala.collect { case i: Number =>
            i.byteValue()
          }.toArray
          if (driverParameters) {
            org.neo4j.driver.Values.vector(coordinateArray)
          } else {
            Values.int8Vector(coordinateArray: _*)
          }
        case "Int16Vector" =>
          val coordinateArray: Array[Short] = coordinates.asScala.collect { case i: Number =>
            i.shortValue()
          }.toArray
          if (driverParameters) {
            org.neo4j.driver.Values.vector(coordinateArray)
          } else {
            Values.int16Vector(coordinateArray: _*)
          }
        case "Int32Vector" =>
          val coordinateArray: Array[Int] = coordinates.asScala.collect { case i: Number =>
            i.intValue()
          }.toArray
          if (driverParameters) {
            org.neo4j.driver.Values.vector(coordinateArray)
          } else {
            Values.int32Vector(coordinateArray: _*)
          }
        case "Int64Vector" =>
          val coordinateArray: Array[Long] = coordinates.asScala.collect { case i: Number =>
            i.longValue()
          }.toArray
          if (driverParameters) {
            org.neo4j.driver.Values.vector(coordinateArray)
          } else {
            Values.int64Vector(coordinateArray: _*)
          }
        case "Float32Vector" =>
          val coordinateArray: Array[Float] = coordinates.asScala.collect { case i: Number =>
            i.floatValue()
          }.toArray
          if (driverParameters) {
            org.neo4j.driver.Values.vector(coordinateArray)
          } else {
            Values.float32Vector(coordinateArray: _*)
          }
        case "Float64Vector" =>
          val coordinateArray: Array[Double] = coordinates.asScala.collect { case i: Number =>
            i.doubleValue()
          }.toArray
          if (driverParameters) {
            org.neo4j.driver.Values.vector(coordinateArray)
          } else {
            Values.float64Vector(coordinateArray: _*)
          }
      }
  }

  private def label[X: P]: P[String] = ":" ~~ symbolicName.!
  private def keyValue[X: P]: P[(String, AnyRef)] = symbolicName ~~ ":" ~ cypherValue

  private def outgoing[X: P]: P[Connection] = ("-" ~~/ relationship ~~/ "->" ~~/ node)
    .map { case (r, n) => Connection(r, n, outgoing = true) }

  private def incoming[X: P]: P[Connection] = ("<-" ~~/ relationship ~~/ "-" ~~/ node)
    .map { case (r, n) => Connection(r, n, outgoing = false) }
  private def symbolicName[X: P]: P[String] = CharsWhileIn("a-zA-Z0-9$_").!

  private def digits[X: P]: P[Unit] = CharsWhileIn("0-9")

  private def floatRepr[X: P]: P[Unit] =
    (digits ~~ "." ~~ digits ~~ exponent.?) |
      ("." ~~ digits ~~ exponent.?) |
      (digits ~~ exponent)
  private def exponent[X: P]: P[Unit] = IgnoreCase("e") ~~ CharsWhileIn("+\\-").? ~~ digits

  final object LimitedWhiteSpace extends Whitespace {

    override def apply(ctx: ParsingRun[_]) = {
      var index = ctx.index
      val input = ctx.input

      while (
        input.isReachable(index) && (
          input(index) match {
            case ' ' | '\t' | '\r' | '\n' | '\f' => true
            case _                               => false
          }
        )
      ) index += 1
      if (ctx.verboseFailures) ctx.reportTerminalMsg(index, Msgs.empty)
      ctx.freshSuccessUnit(index = index)
    }
  }
}
