package com.typesafe.config.impl

/** Minimal mirror of `com.typesafe.config.impl.ConfigImplUtil`. pureconfig's `PeriodUtils` only uses
  * `unicodeTrim`, so that is the sole member provided here.
  */
object ConfigImplUtil {

  /** Faithful-enough reimplementation of Typesafe's `unicodeTrim`: strips leading and trailing
    * whitespace where `Character.isWhitespace` holds. Typesafe additionally treats a leading BOM and
    * non-breaking spaces specially, but pureconfig only feeds ASCII-ish duration strings here, so a
    * `Character.isWhitespace`-based trim matches its behaviour for all inputs it produces.
    */
  def unicodeTrim(s: String): String = {
    val len = s.length
    if (len == 0) return s
    var start = 0
    while (start < len && Character.isWhitespace(s.charAt(start))) start += 1
    var end = len
    while (end > start && Character.isWhitespace(s.charAt(end - 1))) end -= 1
    s.substring(start, end)
  }
}
