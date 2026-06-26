package com.typesafe.config

/** Minimal origin metadata, mirroring the subset of `com.typesafe.config.ConfigOrigin` that
  * pureconfig uses (only for building error messages — never for control flow).
  *
  * The SHocon parser (fastparse) does not retain rich position information, so line numbers are
  * best-effort (`-1` when unknown).
  */
final case class ConfigOrigin(
    private val desc: String,
    private val line: Int = -1,
    private val file: Option[String] = None,
    private val urlOpt: Option[String] = None
) {
  def description(): String = desc
  def lineNumber(): Int = line
  def filename(): String = file.orNull
  def url(): String = urlOpt.orNull

  override def toString(): String = desc
}

object ConfigOrigin {
  def simple(description: String): ConfigOrigin = ConfigOrigin(description)
  def forFile(path: String): ConfigOrigin = ConfigOrigin(path, file = Some(path))
  def forUrl(u: String): ConfigOrigin = ConfigOrigin(u, urlOpt = Some(u))
}
