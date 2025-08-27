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
package org.neo4j.cypher.internal.optionsmap

import org.neo4j.configuration.Config
import org.neo4j.configuration.GraphDatabaseSettings
import org.neo4j.cypher.internal.MapValueOps.Ops
import org.neo4j.dbms.systemgraph.SeedRestoreUntil
import org.neo4j.dbms.systemgraph.SeedURI
import org.neo4j.dbms.systemgraph.allocation.DatabaseAllocationHints
import org.neo4j.kernel.api.exceptions.InvalidArgumentsException
import org.neo4j.kernel.database.NormalizedDatabaseName
import org.neo4j.storageengine.api.StorageEngineFactory
import org.neo4j.storageengine.api.StorageEngineFactory.allAvailableStorageEngines
import org.neo4j.string.SecureString
import org.neo4j.values.AnyValue
import org.neo4j.values.storable._
import org.neo4j.values.virtual.MapValue
import org.neo4j.values.virtual.VirtualValues

import java.util
import java.util.Locale

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava

sealed trait OptionValidator[T] {

  val KEY: String

  protected def validate(value: AnyValue, config: Option[Config])(implicit operation: String): T

  def findIn(optionsMap: MapValue, config: Option[Config])(implicit operation: String): Option[T] = {
    optionsMap
      .find(_._1.equalsIgnoreCase(KEY))
      .map(_._2)
      .flatMap {
        case _: NoValue => None
        case value      => Some(value)
      }
      .map(validate(_, config))
  }
}

trait MapOptionValidator extends OptionValidator[MapValue] {

  protected def validateContent(value: MapValue, config: Option[Config])(implicit operation: String): Unit

  override protected def validate(value: AnyValue, config: Option[Config])(implicit operation: String): MapValue = {
    value match {
      case mapValue: MapValue =>
        validateContent(mapValue, config)
        mapValue
      case _ =>
        throw new InvalidArgumentsException(s"Could not $operation with specified $KEY '$value', Map expected.")
    }
  }
}

trait StringOptionValidator extends OptionValidator[String] {

  protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit

  override protected def validate(value: AnyValue, config: Option[Config])(implicit operation: String): String = {
    value match {
      case textValue: TextValue =>
        validateContent(textValue.stringValue(), config)
        textValue.stringValue()
      case _ => throw InvalidArgumentsException.invalidStringOption(operation, KEY, value)
    }
  }
}

object SeedRestoreUntilOption extends OptionValidator[SeedRestoreUntil] {
  override val KEY: String = "seedRestoreUntil"

  override protected def validate(value: AnyValue, config: Option[Config])(implicit
    operation: String): SeedRestoreUntil = {
    value match {
      case numberValue: NumberValue =>
        SeedRestoreUntil.txId(numberValue.asObject().longValue())
      case dateTimeValue: DateTimeValue =>
        SeedRestoreUntil.datetime(dateTimeValue.asObjectCopy())
      case _ => throw InvalidArgumentsException.invalidSeedRestoreOption(operation, KEY, value)
    }
  }
}

object ExistingDataOption extends StringOptionValidator {
  val KEY = "existingData"

  // possible options:
  val VALID_VALUE = "use"

  // override to keep legacy behaviour. ExistingDataOption is parsed to lowercase, other options keep input casing.
  override protected def validate(value: AnyValue, config: Option[Config])(implicit operation: String): String =
    super.validate(value, config).toLowerCase(Locale.ROOT)

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {
    if (!value.equalsIgnoreCase(VALID_VALUE)) {
      throw InvalidArgumentsException.unrecognisedOptionGivenValue(operation, value, KEY, VALID_VALUE, true)
    }
  }
}

object ExistingSeedInstanceOption extends StringOptionValidator {
  override val KEY: String = "existingDataSeedInstance"

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {}
}

object ExistingSeedServerOption extends StringOptionValidator {
  override val KEY: String = "existingDataSeedServer"

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {}
}

object StoreFormatOption extends StringOptionValidator {
  override val KEY: String = "storeFormat"

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {
    try {
      // Validate the format by looking for a storage engine that supports it - will throw if none was found
      val selectEngineConfig = Config.newBuilder()
        .set(GraphDatabaseSettings.db_format, value)
        .build()
      StorageEngineFactory.selectStorageEngine(selectEngineConfig)
    } catch {
      case _: Exception =>
        val allFormatsList: java.util.List[String] = allAvailableStorageEngines().asScala
          .flatMap(sef => sef.supportedFormats(false).asScala.toSeq.sorted)
          .toSeq.distinct.asJava
        throw InvalidArgumentsException.invalidOptionFormat(operation, KEY, value, allFormatsList)
    }
  }
}

object SeedURIOption extends OptionValidator[SeedURI] {
  override val KEY: String = "seedURI"

  override protected def validate(value: AnyValue, config: Option[Config])(implicit operation: String): SeedURI = {
    value match {
      case textValue: TextValue => SeedURI.single(textValue.stringValue())
      case mapValue: MapValue =>
        val map = new util.HashMap[String, String](mapValue.size())
        mapValue.foreachEntry((k, v) =>
          map.put(
            k,
            v match {
              case text: TextValue => text.stringValue()
              case _ =>
                throw InvalidArgumentsException.invalidStringOption(operation, "'" + k + "' in '" + KEY + "'", v)
            }
          )
        )
        SeedURI.sharded(map)
      case _ => throw InvalidArgumentsException.invalidStringOption(operation, KEY, value)
    }
  }

  def validateSingleOnly(seedURI: SeedURI)(implicit operation: String): SeedURI = {
    val uriMap = seedURI.uriMap
    if (!uriMap.isEmpty) {
      val keys: Array[String] = uriMap.keySet.toArray(new Array[String](0))
      val values: Array[AnyValue] = keys.map(uriMap.get).map(Values.stringValue)
      throw InvalidArgumentsException.invalidStringOption(operation, KEY, VirtualValues.map(keys, values))
    }
    seedURI
  }
}

object SeedSourceDatabaseOption extends OptionValidator[NormalizedDatabaseName] {
  override val KEY: String = "seedSourceDatabase"

  override protected def validate(value: AnyValue, config: Option[Config])(implicit
    operation: String): NormalizedDatabaseName = {
    value match {
      case text: TextValue =>
        new NormalizedDatabaseName(text.stringValue())
      case _ => throw InvalidArgumentsException.invalidStringOption(operation, KEY, value)
    }
  }
}

object SeedCredentialsOption extends OptionValidator[SecureString] {
  override val KEY: String = "seedCredentials"

  override protected def validate(value: AnyValue, config: Option[Config])(implicit operation: String): SecureString = {
    value match {
      case text: TextValue =>
        new SecureString(text.stringValue())
      case _ => throw InvalidArgumentsException.invalidStringOption(operation, KEY, value)
    }
  }
}

object SeedConfigOption extends StringOptionValidator {
  override val KEY: String = "seedConfig"

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {
    // no content validation, any string is accepted
  }
}

object ExistingMetadataOption extends StringOptionValidator {
  val KEY = "existingMetadata"

  // possible options:
  val VALID_VALUE = "use"

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {
    if (!value.equalsIgnoreCase(VALID_VALUE)) {
      throw InvalidArgumentsException.unrecognisedOptionGivenValue(operation, KEY, value, VALID_VALUE, true)
    }
  }
}

object LogEnrichmentOption extends StringOptionValidator {
  override val KEY: String = "txLogEnrichment"

  private val FULL: String = "FULL"
  private val DIFF: String = "DIFF"
  private val OFF: String = "OFF"
  private val validValues = Seq(FULL, DIFF, OFF)

  // override to normalize to uppercase.
  override protected def validate(value: AnyValue, config: Option[Config])(implicit operation: String): String =
    super.validate(value, config).toUpperCase(Locale.ROOT)

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {
    if (!validValues.exists(value.equalsIgnoreCase)) {
      throw InvalidArgumentsException.unrecognisedOptionGivenValue(operation, KEY, value, validValues.asJava)
    }
  }

}

object AllocationHintsOption extends MapOptionValidator {
  override val KEY: String = "allocationHints"

  override protected def validateContent(value: MapValue, config: Option[Config])(implicit operation: String): Unit = {
    value.foreachEntry((k, v) => DatabaseAllocationHints.validate(k, v))
  }
}

object ExternalIdentityOption extends StringOptionValidator {
  override val KEY: String = "externalIdentity"

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {
    // no content validation, any string is accepted
  }
}

object BackpressureEnabledOption extends StringOptionValidator {
  val KEY = "backpressureEnabled"

  // possible options:
  val VALID_VALUE = "true"

  override protected def validateContent(value: String, config: Option[Config])(implicit operation: String): Unit = {
    if (!value.equalsIgnoreCase(VALID_VALUE)) {
      throw InvalidArgumentsException.unrecognisedOptionGivenValue(operation, KEY, value, VALID_VALUE, true)
    }
  }
}
