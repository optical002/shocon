package com.typesafe.config
package impl

/** Rewrites HOCON substitution syntax into a form the SHocon fastparse grammar accepts.
  *
  * The SHocon parser only tolerates `${...}` when it sits *inside* a quoted string
  * (`a = "${b}"` parses to `StringLiteral("${b}")`). The bare form `a = ${b}` and the
  * concatenation form `a = "x."${b}` are parse errors. Typesafe HOCON (and the pureconfig
  * fixtures) use exactly those unquoted forms.
  *
  * This preprocessor scans each line's value (the text after the first unquoted `=`/`:`) and, when
  * it contains an unquoted `${`, collapses the whole value expression into a single double-quoted
  * string: quoted segments contribute their inner text, bare segments contribute verbatim, and
  * `${...}` tokens are kept inline. [[SubstitutionResolver]] then re-scans that literal at
  * `resolve()` time. Examples:
  *   - `host = "myhost1."${host-suffix}`  →  `host = "myhost1.${host-suffix}"`
  *   - `host = ${?host-override}`         →  `host = "${?host-override}"`
  *
  * Limitations (acceptable for this fork): values are processed per line, so a substitution split
  * across lines is not handled; `${` already inside quotes is left untouched (so a string that
  * legitimately contains `${...}` will still be treated as a substitution by the resolver — the
  * documented re-scan trade-off).
  */
private[config] object SubstitutionPreprocessor {

  /** Rewrite any unquoted `${...}` value expressions in `text`. No-op fast path when absent. */
  def rewrite(text: String): String =
    if (!text.contains("${")) text
    else text.linesIterator.map(rewriteLine).mkString("\n")

  private def rewriteLine(line: String): String = {
    // Find the first unquoted key/value separator.
    val sepIdx = firstUnquotedSeparator(line)
    if (sepIdx < 0) return line // not a `key = value` line (could be `{`, `}`, array item, etc.)

    val key = line.substring(0, sepIdx + 1) // include the separator
    val value = line.substring(sepIdx + 1)

    // Only rewrite when the value actually contains an unquoted ${.
    if (!hasUnquotedSubst(value)) line
    else key + rewriteValue(value)
  }

  /** Index of the first `=` or `:` that is not inside quotes, or -1. */
  private def firstUnquotedSeparator(s: String): Int = {
    var i = 0
    var inQuote = false
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"' && !isEscaped(s, i)) inQuote = !inQuote
      else if (!inQuote && (c == '=' || c == ':')) return i
      i += 1
    }
    -1
  }

  private def hasUnquotedSubst(value: String): Boolean = {
    var i = 0
    var inQuote = false
    while (i < value.length - 1) {
      val c = value.charAt(i)
      if (c == '"' && !isEscaped(value, i)) inQuote = !inQuote
      else if (!inQuote && c == '$' && value.charAt(i + 1) == '{') return true
      i += 1
    }
    false
  }

  /** Collapse a value expression containing unquoted `${...}` into one quoted string.
    *
    * Stops the value at an unquoted comment (`#` or `//`) or trailing comma, preserving that tail
    * outside the quotes.
    */
  private def rewriteValue(value: String): String = {
    val (body, tail) = splitTrailing(value)
    val sb = new StringBuilder
    var i = 0
    var inQuote = false
    while (i < body.length) {
      val c = body.charAt(i)
      if (c == '"' && !isEscaped(body, i)) {
        inQuote = !inQuote
        // drop the quote character itself (its inner content is emitted verbatim)
      } else {
        sb.append(c)
      }
      i += 1
    }
    // Leading/trailing whitespace around the (now unquoted) value is insignificant; trim it so the
    // wrapped literal is clean, then re-wrap in quotes.
    " \"" + sb.toString.trim + "\"" + tail
  }

  /** Split off a trailing unquoted comment or comma so it stays outside the rewritten quotes. */
  private def splitTrailing(value: String): (String, String) = {
    var i = 0
    var inQuote = false
    while (i < value.length) {
      val c = value.charAt(i)
      if (c == '"' && !isEscaped(value, i)) inQuote = !inQuote
      else if (!inQuote) {
        if (c == '#') return (value.substring(0, i), value.substring(i))
        if (c == '/' && i + 1 < value.length && value.charAt(i + 1) == '/')
          return (value.substring(0, i), value.substring(i))
      }
      i += 1
    }
    (value, "")
  }

  private def isEscaped(s: String, i: Int): Boolean = {
    var backslashes = 0
    var j = i - 1
    while (j >= 0 && s.charAt(j) == '\\') { backslashes += 1; j -= 1 }
    backslashes % 2 == 1
  }
}
