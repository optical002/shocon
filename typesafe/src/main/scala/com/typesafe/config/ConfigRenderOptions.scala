package com.typesafe.config

import org.akkajs.{shocon => sh}

/** Rendering options, mirroring `com.typesafe.config.ConfigRenderOptions`.
  *
  * pureconfig only uses `defaults()` and `concise()`; the chainable setters are kept for source
  * compatibility. The renderer always emits compact JSON-ish HOCON, which is sufficient for
  * `saveConfigAsPropertyFile` and error diagnostics.
  */
final case class ConfigRenderOptions(
    originComments: Boolean = true,
    comments: Boolean = true,
    formatted: Boolean = true,
    json: Boolean = true
) {
  def setOriginComments(value: Boolean): ConfigRenderOptions = copy(originComments = value)
  def setComments(value: Boolean): ConfigRenderOptions = copy(comments = value)
  def setFormatted(value: Boolean): ConfigRenderOptions = copy(formatted = value)
  def setJson(value: Boolean): ConfigRenderOptions = copy(json = value)
}

object ConfigRenderOptions {
  def defaults(): ConfigRenderOptions = ConfigRenderOptions()
  def concise(): ConfigRenderOptions =
    ConfigRenderOptions(originComments = false, comments = false, formatted = false, json = true)
}

/** Recursive JSON-ish renderer over the SHocon AST. */
private[config] object ConfigRenderer {

  def render(v: sh.Config.Value, options: ConfigRenderOptions): String = {
    val sb = new StringBuilder
    if (options.formatted) renderPretty(v, options, sb, 0) else renderCompact(v, sb)
    sb.toString()
  }

  private def renderCompact(v: sh.Config.Value, sb: StringBuilder): Unit =
    v match {
      case sh.Config.NullLiteral => sb.append("null")
      case sh.Config.BooleanLiteral(b) => sb.append(b)
      case sh.Config.NumberLiteral(s) => sb.append(s)
      case sh.Config.StringLiteral(s) => quote(s, sb)
      case sh.Config.Array(elems) =>
        sb.append('[')
        var first = true
        elems.foreach { e =>
          if (!first) sb.append(',')
          first = false
          renderCompact(e, sb)
        }
        sb.append(']')
      case sh.Config.Object(fields) =>
        sb.append('{')
        var first = true
        fields.foreach { case (k, fv) =>
          if (!first) sb.append(',')
          first = false
          quote(k, sb)
          sb.append(':')
          renderCompact(fv, sb)
        }
        sb.append('}')
    }

  private def renderPretty(
      v: sh.Config.Value,
      options: ConfigRenderOptions,
      sb: StringBuilder,
      indent: Int
  ): Unit =
    v match {
      case sh.Config.Array(elems) if elems.nonEmpty =>
        sb.append("[\n")
        var first = true
        elems.foreach { e =>
          if (!first) sb.append(",\n")
          first = false
          pad(sb, indent + 1)
          renderPretty(e, options, sb, indent + 1)
        }
        sb.append('\n')
        pad(sb, indent)
        sb.append(']')
      case sh.Config.Object(fields) if fields.nonEmpty =>
        sb.append("{\n")
        var first = true
        fields.foreach { case (k, fv) =>
          if (!first) sb.append(",\n")
          first = false
          pad(sb, indent + 1)
          quote(k, sb)
          sb.append(" : ")
          renderPretty(fv, options, sb, indent + 1)
        }
        sb.append('\n')
        pad(sb, indent)
        sb.append('}')
      case scalar => renderCompact(scalar, sb)
    }

  private def pad(sb: StringBuilder, indent: Int): Unit = {
    var i = 0
    while (i < indent) { sb.append("    "); i += 1 }
  }

  private def quote(s: String, sb: StringBuilder): Unit = {
    sb.append('"')
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"' => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c => sb.append(c)
      }
      i += 1
    }
    sb.append('"')
  }
}
