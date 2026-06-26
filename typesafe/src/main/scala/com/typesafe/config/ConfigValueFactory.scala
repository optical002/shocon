package com.typesafe.config

import java.{lang => jl, util => ju}

import scala.jdk.CollectionConverters._

import org.akkajs.{shocon => sh}

/** Factory for `ConfigValue`s, mirroring `com.typesafe.config.ConfigValueFactory`. */
object ConfigValueFactory {

  /** Converts an arbitrary plain value into a `ConfigValue`, recursively. Mirrors
    * `ConfigValueFactory.fromAnyRef`. Accepts `Any` (not just `Object`) so callers can pass
    * primitives like `Long`/`Double` and rely on autoboxing, matching how pureconfig calls it
    * (e.g. `Try(s.toLong).map(ConfigValueFactory.fromAnyRef)`).
    */
  def fromAnyRef(o: Any): ConfigValue = ConfigValue(toShocon(o.asInstanceOf[Object]))

  /** Builds a `ConfigList` from a Java list of plain values (mirrors `fromIterable`). The Typesafe
    * contract accepts plain values, not `ConfigValue`s, and wraps them.
    */
  def fromIterable(values: jl.Iterable[?]): ConfigList = {
    val elems = values.asScala.iterator.map(v => toShocon(v.asInstanceOf[Object])).toVector
    ConfigList(sh.Config.Array(elems), ConfigOrigin.simple("iterable"))
  }

  /** Builds a `ConfigObject` from a Java map of plain values (mirrors `fromMap`). */
  def fromMap(values: ju.Map[String, ?]): ConfigObject = {
    val fields = values.asScala.iterator.map { case (k, v) =>
      k -> toShocon(v.asInstanceOf[Object])
    }.toMap
    ConfigObject(sh.Config.Object(fields), ConfigOrigin.simple("map"))
  }

  private def toShocon(o: Object): sh.Config.Value =
    o match {
      case null => sh.Config.NullLiteral
      case cv: ConfigValue => cv.inner
      case b: jl.Boolean => sh.Config.BooleanLiteral(b.booleanValue())
      case s: String => sh.Config.StringLiteral(s)
      case n: jl.Double => sh.Config.NumberLiteral(numToString(n.doubleValue()))
      case n: jl.Float => sh.Config.NumberLiteral(numToString(n.doubleValue()))
      case n: Number => sh.Config.NumberLiteral(n.toString)
      case m: ju.Map[_, _] =>
        val fields = m.asScala.iterator.map { case (k, v) =>
          k.toString -> toShocon(v.asInstanceOf[Object])
        }.toMap
        sh.Config.Object(fields)
      case it: jl.Iterable[_] =>
        sh.Config.Array(it.asScala.iterator.map(v => toShocon(v.asInstanceOf[Object])).toVector)
      case other => sh.Config.StringLiteral(other.toString)
    }

  // Render whole doubles without a trailing ".0" so unwrapped round-trips to Long where appropriate.
  private def numToString(d: Double): String =
    if (d == Math.floor(d) && !d.isInfinite) d.toLong.toString else d.toString
}
