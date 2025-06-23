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
package org.neo4j.cypher.internal.expressions

sealed trait VectorDistanceMetric {
  def metricName: String
  def description: String = s"$metricName"
  override def toString: String = description
}

object VectorDistanceMetric {

  def unapply(name: String): Option[VectorDistanceMetric] = name match {
    case EuclideanVectorDistanceMetric.metricName        => Some(EuclideanVectorDistanceMetric)
    case EuclideanSquaredVectorDistanceMetric.metricName => Some(EuclideanSquaredVectorDistanceMetric)
    case ManhattanVectorDistanceMetric.metricName        => Some(ManhattanVectorDistanceMetric)
    case CosineVectorDistanceMetric.metricName           => Some(CosineVectorDistanceMetric)
    case DotVectorDistanceMetric.metricName              => Some(DotVectorDistanceMetric)
    case HammingVectorDistanceMetric.metricName          => Some(HammingVectorDistanceMetric)
    case _                                               => None
  }
}

case object EuclideanVectorDistanceMetric extends VectorDistanceMetric {
  val metricName: String = "EUCLIDEAN"
}

case object EuclideanSquaredVectorDistanceMetric extends VectorDistanceMetric {
  val metricName: String = "EUCLIDEAN_SQUARED"
}

case object ManhattanVectorDistanceMetric extends VectorDistanceMetric {
  val metricName: String = "MANHATTAN"
}

case object CosineVectorDistanceMetric extends VectorDistanceMetric {
  val metricName: String = "COSINE"
}

case object DotVectorDistanceMetric extends VectorDistanceMetric {
  val metricName: String = "DOT"
}

case object HammingVectorDistanceMetric extends VectorDistanceMetric {
  val metricName: String = "HAMMING"
}
