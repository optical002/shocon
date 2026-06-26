package com.typesafe.config

/** Mirrors `com.typesafe.config.ConfigMemorySize`. */
final class ConfigMemorySize private (val bytes: Long) {
  def toBytes: Long = bytes
  override def equals(o: Any): Boolean = o match {
    case other: ConfigMemorySize => other.bytes == bytes
    case _ => false
  }
  override def hashCode(): Int = java.lang.Long.hashCode(bytes)
  override def toString(): String = s"ConfigMemorySize($bytes)"
}

object ConfigMemorySize {
  def ofBytes(bytes: Long): ConfigMemorySize = new ConfigMemorySize(bytes)
}
