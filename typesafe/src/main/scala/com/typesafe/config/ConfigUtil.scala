package com.typesafe.config

import java.{util => ju}

import scala.collection.mutable

/** Path-expression helpers, mirroring `com.typesafe.config.ConfigUtil`.
  *
  * A path is dot-separated keys; keys that contain dots (or are empty/special) are double-quoted.
  */
object ConfigUtil {

  /** Splits a path expression into its keys, honouring quoted segments. */
  def splitPath(path: String): ju.List[String] = {
    val out = new ju.ArrayList[String]()
    val cur = new StringBuilder
    var inQuotes = false
    var i = 0
    var sawAny = false
    while (i < path.length) {
      val c = path.charAt(i)
      c match {
        case '"' =>
          inQuotes = !inQuotes
          sawAny = true
        case '.' if !inQuotes =>
          out.add(cur.toString)
          cur.setLength(0)
          sawAny = false
        case other =>
          cur.append(other)
          sawAny = true
      }
      i += 1
    }
    if (sawAny || out.isEmpty && path.nonEmpty) out.add(cur.toString)
    else if (cur.nonEmpty) out.add(cur.toString)
    out
  }

  /** Joins keys into a path expression, quoting keys that need it. */
  def joinPath(elements: String*): String =
    elements.map(renderKey).mkString(".")

  /** Joins keys provided as a Java list. */
  def joinPath(elements: ju.List[String]): String = {
    val sb = new mutable.StringBuilder
    val it = elements.iterator()
    var first = true
    while (it.hasNext) {
      if (!first) sb.append('.')
      first = false
      sb.append(renderKey(it.next()))
    }
    sb.toString()
  }

  private def renderKey(key: String): String =
    if (key.isEmpty || key.exists(c => c == '.' || c == '"' || c == '$' || Character.isWhitespace(c)))
      "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    else key
}
