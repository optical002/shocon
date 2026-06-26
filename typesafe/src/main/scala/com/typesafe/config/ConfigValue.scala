package com.typesafe.config

import java.{lang => jl, util => ju}

import scala.jdk.CollectionConverters._

import org.akkajs.{shocon => sh}

/** A configuration value, mirroring `com.typesafe.config.ConfigValue`.
  *
  * Backed by a SHocon AST node (`org.akkajs.shocon.Config.Value`). The single most important
  * contract here is [[unwrapped]]: pureconfig's `ConfigCursor` pattern-matches the result against
  * boxed Java types (`java.lang.Long`, `java.lang.Integer`, `java.lang.Double`, `String`,
  * `Boolean`), so we box exactly like Typesafe Config does — integral numbers as `java.lang.Long`,
  * fractional as `java.lang.Double`, strings raw and uncoerced.
  */
trait ConfigValue extends ConfigMergeable {

  /** The backing SHocon AST node. */
  def inner: sh.Config.Value

  /** The origin of this value (best-effort; used only for diagnostics). Parameterless so
    * pureconfig's mixed `value.origin` / `value.origin()` Java-style calls both resolve.
    */
  def origin(): ConfigOrigin

  def valueType(): ConfigValueType =
    inner match {
      case _: sh.Config.Object => ConfigValueType.OBJECT
      case _: sh.Config.Array => ConfigValueType.LIST
      case _: sh.Config.NumberLiteral => ConfigValueType.NUMBER
      case _: sh.Config.StringLiteral => ConfigValueType.STRING
      case _: sh.Config.BooleanLiteral => ConfigValueType.BOOLEAN
      case sh.Config.NullLiteral => ConfigValueType.NULL
    }

  /** The unwrapped plain value, boxed with Typesafe Config semantics. Declared without parens so
    * pureconfig's `value.unwrapped` (no-paren, Java-style) calls resolve.
    */
  def unwrapped: Object = ConfigValue.unwrap(inner)

  def render(): String = ConfigRenderer.render(inner, ConfigRenderOptions.defaults())
  def render(options: ConfigRenderOptions): String = ConfigRenderer.render(inner, options)

  /** Returns a copy of this value with a different origin. */
  def withOrigin(o: ConfigOrigin): ConfigValue = ConfigValue(inner, o)

  /** Merges another value as a fallback under this one (this wins), mirroring
    * `ConfigMergeable.withFallback`. When both this and `other` are objects, their fields are merged
    * deeply (this taking precedence); otherwise a non-object value ignores the fallback, matching
    * Typesafe semantics.
    */
  def withFallback(other: ConfigMergeable): ConfigValue =
    inner match {
      case o: sh.Config.Object =>
        ConfigValue.asObject(other) match {
          case Some(oo) => ConfigValue(sh.Config.Object.mergeConfigs(oo, o), origin())
          case None => this
        }
      case _ => this
    }

  /** Wraps this value into a single-key `Config`, mirroring `ConfigValue.atKey`. */
  def atKey(key: String): Config =
    Config(sh.Config.Object(Map(key -> inner)))

  override def toString(): String = s"ConfigValue(${render()})"
}

object ConfigValue {

  private val syntheticOrigin = ConfigOrigin.simple("ConfigValue")

  /** Wraps a SHocon AST node in the appropriately-typed `ConfigValue`. */
  def apply(v: sh.Config.Value, origin: ConfigOrigin = syntheticOrigin): ConfigValue =
    v match {
      case o: sh.Config.Object => ConfigObject(o, origin)
      case a: sh.Config.Array => ConfigList(a, origin)
      case other => SimpleConfigValue(other, origin)
    }

  /** Extracts the backing SHocon object from a fallback that is either a `ConfigValue` wrapping an
    * object or a `Config`; anything else has no object to merge.
    */
  private def asObject(m: ConfigMergeable): Option[sh.Config.Object] =
    m match {
      case c: Config => Some(c.shoconObject)
      case cv: ConfigValue =>
        cv.inner match {
          case o: sh.Config.Object => Some(o)
          case _ => None
        }
      case _ => None
    }

  /** Boxed, Typesafe-style unwrapping of a SHocon AST node. */
  private[config] def unwrap(v: sh.Config.Value): Object =
    v match {
      case sh.Config.NullLiteral => null
      case sh.Config.BooleanLiteral(b) => jl.Boolean.valueOf(b)
      case sh.Config.StringLiteral(s) => s
      case sh.Config.NumberLiteral(s) => boxNumber(s)
      case sh.Config.Object(fields) =>
        val m = new ju.LinkedHashMap[String, Object]()
        fields.foreach { case (k, fv) => m.put(k, unwrap(fv)) }
        m
      case sh.Config.Array(elems) =>
        val l = new ju.ArrayList[Object](elems.size)
        elems.foreach(e => l.add(unwrap(e)))
        l
    }

  /** Integral literals box to `java.lang.Long`, fractional to `java.lang.Double` (Typesafe rule). */
  private def boxNumber(s: String): Object =
    if (s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0)
      try jl.Long.valueOf(jl.Long.parseLong(s))
      catch { case _: NumberFormatException => jl.Double.valueOf(jl.Double.parseDouble(s)) }
    else
      jl.Double.valueOf(jl.Double.parseDouble(s))
}

/** A scalar (string/number/boolean/null) config value. */
final case class SimpleConfigValue(inner: sh.Config.Value, private val o: ConfigOrigin)
    extends ConfigValue {
  def origin(): ConfigOrigin = o
}

/** A config object, also a `java.util.Map[String, ConfigValue]`. */
final class ConfigObject private (val shoconObject: sh.Config.Object, private val o: ConfigOrigin)
    extends ju.AbstractMap[String, ConfigValue]
    with ConfigValue {

  def inner: sh.Config.Value = shoconObject
  def origin(): ConfigOrigin = o
  override def valueType(): ConfigValueType = ConfigValueType.OBJECT

  override def unwrapped: ju.Map[String, Object] = {
    val m = new ju.LinkedHashMap[String, Object]()
    shoconObject.fields.foreach { case (k, v) => m.put(k, ConfigValue.unwrap(v)) }
    m
  }

  override def entrySet(): ju.Set[ju.Map.Entry[String, ConfigValue]] = {
    val s = new ju.LinkedHashSet[ju.Map.Entry[String, ConfigValue]]()
    shoconObject.fields.foreach { case (k, v) =>
      s.add(new ju.AbstractMap.SimpleImmutableEntry(k, ConfigValue(v, o)))
    }
    s
  }

  override def get(key: Any): ConfigValue =
    shoconObject.fields.get(key.asInstanceOf[String]) match {
      case Some(v) => ConfigValue(v, o)
      case None => null
    }

  /** Returns a copy of this object without the given key. */
  def withoutKey(key: String): ConfigObject =
    ConfigObject(sh.Config.Object(shoconObject.fields - key), o)

  /** Wraps this object as a `Config`. */
  def toConfig: Config = Config(shoconObject)

  override def withOrigin(newOrigin: ConfigOrigin): ConfigValue =
    ConfigObject(shoconObject, newOrigin)
}

object ConfigObject {
  def apply(o: sh.Config.Object, origin: ConfigOrigin): ConfigObject = new ConfigObject(o, origin)
}

/** A config list, also a `java.util.List[ConfigValue]`. */
final class ConfigList private (val shoconArray: sh.Config.Array, private val o: ConfigOrigin)
    extends ju.AbstractList[ConfigValue]
    with ConfigValue {

  private val elems: IndexedSeq[sh.Config.Value] = shoconArray.elements.toIndexedSeq

  def inner: sh.Config.Value = shoconArray
  def origin(): ConfigOrigin = o
  override def valueType(): ConfigValueType = ConfigValueType.LIST

  override def get(index: Int): ConfigValue = ConfigValue(elems(index), o)
  override def size(): Int = elems.size

  override def unwrapped: ju.List[Object] = {
    val l = new ju.ArrayList[Object](elems.size)
    elems.foreach(e => l.add(ConfigValue.unwrap(e)))
    l
  }

  override def withOrigin(newOrigin: ConfigOrigin): ConfigValue =
    ConfigList(shoconArray, newOrigin)
}

object ConfigList {
  def apply(a: sh.Config.Array, origin: ConfigOrigin): ConfigList = new ConfigList(a, origin)
}
