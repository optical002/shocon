package com.typesafe.config

/** Mirrors `com.typesafe.config.ConfigException`. pureconfig's `ErrorUtil` pattern-matches on the
  * `IO` and `Parse` subtypes and reads `origin()`, so those are provided faithfully.
  */
abstract class ConfigException(
    val configOrigin: ConfigOrigin,
    message: String,
    cause: Throwable
) extends RuntimeException(message, cause) {
  def origin(): ConfigOrigin = configOrigin
}

object ConfigException {

  final class IO(origin: ConfigOrigin, message: String, cause: Throwable)
      extends ConfigException(origin, message, cause) {
    def this(origin: ConfigOrigin, message: String) = this(origin, message, null)
  }
  object IO {
    def unapply(e: IO): Boolean = true
  }

  class Parse(origin: ConfigOrigin, message: String, cause: Throwable)
      extends ConfigException(origin, message, cause) {
    def this(origin: ConfigOrigin, message: String) = this(origin, message, null)
  }
  object Parse {
    def unapply(e: Parse): Boolean = true
  }

  /** Thrown when a required `${path}` substitution cannot be resolved to a value. Extends [[Parse]]
    * so pureconfig's `ErrorUtil` maps it to a `CannotParse` failure. The message is the bare
    * Typesafe-style text (`Could not resolve substitution to a value: ${path}`) with no trailing
    * period, since `ErrorUtil` only strips an origin prefix and a single trailing `.`.
    */
  final class UnresolvedSubstitution(origin: ConfigOrigin, val expression: String)
      extends Parse(origin, s"Could not resolve substitution to a value: $${$expression}", null)

  final class Missing(origin: ConfigOrigin, val path: String)
      extends ConfigException(origin, s"No configuration setting found for key '$path'", null)

  final class BadValue(origin: ConfigOrigin, message: String)
      extends ConfigException(origin, message, null) {
    def this(message: String) = this(ConfigOrigin.simple("ConfigException"), message)
  }

  final class BugOrBroken(message: String)
      extends ConfigException(ConfigOrigin.simple("ConfigException"), message, null)
}
