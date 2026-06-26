package com.typesafe.config

/** Parse options, mirroring the subset of `com.typesafe.config.ConfigParseOptions` pureconfig uses.
  * Only `allowMissing` affects behaviour; `classLoader` is a no-op carrier on Native.
  */
final case class ConfigParseOptions(
    allowMissing: Boolean = true,
    classLoader: ClassLoader = null
) {
  def setAllowMissing(value: Boolean): ConfigParseOptions = copy(allowMissing = value)
  def setClassLoader(loader: ClassLoader): ConfigParseOptions = copy(classLoader = loader)
}

object ConfigParseOptions {
  def defaults: ConfigParseOptions = ConfigParseOptions()
}
