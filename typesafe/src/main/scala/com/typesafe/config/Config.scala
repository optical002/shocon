package com.typesafe.config

import java.{lang => jl}

import org.akkajs.{shocon => sh}

/** A configuration tree, mirroring the subset of `com.typesafe.config.Config` that pureconfig uses.
  *
  * Backed by a SHocon `Config.Object`. pureconfig drives everything through `root()` (to get a
  * `ConfigObject` it then navigates with its own cursor), `resolve()`, `withFallback`, and — for the
  * generated `ProductWriters` and the memory-size reader — `withValue` and `getBytes`.
  */
final class Config private (private val obj: sh.Config.Object, private val origin: ConfigOrigin)
    extends ConfigMergeable {

  /** The backing SHocon object (used by the value-tree merge in `ConfigValue.withFallback`). */
  private[config] def shoconObject: sh.Config.Object = obj

  /** The root object of this config. */
  def root(): ConfigObject = ConfigObject(obj, origin)

  /** Substitution resolution. Walks the (already-merged) value tree resolving HOCON
    * `${path}` / `${?path}` references — see [[impl.SubstitutionResolver]]. Runs here, after any
    * `withFallback` merges, so a substitution may reference a value supplied by a fallback config.
    *
    * @throws ConfigException.UnresolvedSubstitution if a required `${path}` cannot be resolved.
    */
  def resolve(): Config =
    new Config(impl.SubstitutionResolver.resolve(obj), origin)

  /** Merges another config under this one (this wins), mirroring `Config.withFallback`. */
  def withFallback(other: Config): Config =
    if (other == null) this
    else new Config(sh.Config.Object.mergeConfigs(other.obj, obj), origin)

  /** Returns a copy of this config with `path` set to `value`. Used by generated product writers. */
  def withValue(path: String, value: ConfigValue): Config = {
    val added = sh.Config.Object.fromPairs(Seq(path -> value.inner))
    new Config(sh.Config.Object.mergeConfigs(obj, added), origin)
  }

  def hasPath(path: String): Boolean = lookup(path).isDefined

  /** Parses a size-in-bytes value at `path` (mirrors `Config.getBytes`). */
  def getBytes(path: String): jl.Long = {
    val raw = lookup(path) match {
      case Some(sh.Config.StringLiteral(s)) => s
      case Some(sh.Config.NumberLiteral(s)) => s
      case Some(other) => other.toString
      case None => throw new ConfigException.Missing(origin, path)
    }
    jl.Long.valueOf(Config.parseBytes(raw, path, origin))
  }

  def checkValid(reference: Config, restrictToPaths: String*): Unit = ()

  private def lookup(path: String): Option[sh.Config.Value] = {
    import sh.ConfigOps
    (obj: sh.Config.Value).get(path)
  }
}

object Config {

  def apply(v: sh.Config.Value): Config = apply(v, ConfigOrigin.simple("Config"))

  def apply(v: sh.Config.Value, origin: ConfigOrigin): Config =
    v match {
      case o: sh.Config.Object => new Config(o, origin)
      case other => new Config(sh.Config.Object(Map("" -> other)), origin)
    }

  private[config] def empty(origin: ConfigOrigin = ConfigOrigin.simple("empty config")): Config =
    new Config(sh.Config.Object(Map.empty), origin)

  /** Parses a HOCON size-in-bytes string (e.g. "10M", "512KiB"). Mirrors Typesafe's parseBytes. */
  private[config] def parseBytes(input: String, path: String, origin: ConfigOrigin): Long = {
    val s = input.trim
    val unitString = getUnits(s)
    val numberString = s.substring(0, s.length - unitString.length).trim
    if (numberString.isEmpty)
      throw new ConfigException.BadValue(origin, s"No number in size-in-bytes value '$input'")
    val units = MemoryUnit.parseUnit(unitString).getOrElse(
      throw new ConfigException.BadValue(origin, s"Could not parse size unit '$unitString' in '$input'")
    )
    try {
      val result: BigInt =
        if (numberString.matches("[0-9]+")) units.bytes * BigInt(numberString)
        else (BigDecimal(units.bytes) * BigDecimal(numberString)).toBigInt
      if (result.bitLength < 64) result.longValue
      else throw new ConfigException.BadValue(origin, s"size-in-bytes value out of range: '$input'")
    } catch {
      case _: NumberFormatException =>
        throw new ConfigException.BadValue(origin, s"Could not parse size number '$numberString'")
    }
  }

  private def getUnits(s: String): String = {
    var i = s.length - 1
    while (i >= 0 && Character.isLetter(s.charAt(i))) i -= 1
    s.substring(i + 1)
  }
}
