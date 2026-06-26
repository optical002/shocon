package com.typesafe.config

/** The type of a `ConfigValue`, mirroring `com.typesafe.config.ConfigValueType`. */
final class ConfigValueType private (val name: String) {
  override def toString(): String = name
}

object ConfigValueType {
  val OBJECT: ConfigValueType = new ConfigValueType("OBJECT")
  val LIST: ConfigValueType = new ConfigValueType("LIST")
  val NUMBER: ConfigValueType = new ConfigValueType("NUMBER")
  val BOOLEAN: ConfigValueType = new ConfigValueType("BOOLEAN")
  val NULL: ConfigValueType = new ConfigValueType("NULL")
  val STRING: ConfigValueType = new ConfigValueType("STRING")
}
