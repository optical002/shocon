package com.typesafe.config
package impl

import scala.collection.compat._

import org.akkajs.{shocon => sh}

/** Resolves HOCON `${path}` / `${?path}` substitutions over a SHocon value tree.
  *
  * The SHocon fastparse grammar has no substitution node: it collapses `${x}` into a literal
  * `StringLiteral("${x}")`. Rather than extend the grammar, this resolver re-scans every
  * `StringLiteral` for substitution syntax after parsing (and, crucially, after `withFallback`
  * merges — `Config.resolve()` is the single call site). Trade-off: a string that legitimately
  * contains `${...}` is treated as a substitution. That is an accepted limitation of this fork.
  *
  * Supported:
  *   - required (`${p}`) and optional (`${?p}`) substitutions, paths absolute from the root;
  *   - recursive resolution (a referenced value may itself contain substitutions) with cycle
  *     detection;
  *   - environment-variable fallback (`System.getenv(path)`) when a path is absent from the config;
  *   - type preservation when a value is exactly one substitution (`x = ${y}` yields y's real
  *     object/array/number/bool, not a string);
  *   - string concatenation when literal text and substitutions are mixed (`"a."${b}`).
  *
  * Not supported in this version: array/object concatenation with a *leading* bare `${a}` adjacent
  * to a `[...]`/`{...}` literal (the pieces do not survive as adjacent AST siblings under the
  * re-scan strategy); `$${` escaping.
  */
private[config] object SubstitutionResolver {

  /** A piece of a scanned string literal: either raw text or a substitution reference. */
  private sealed trait Piece
  private final case class Text(value: String) extends Piece
  private final case class Subst(path: String, optional: Boolean) extends Piece

  /** Resolve all substitutions in `root`, returning a fully-resolved object.
    *
    * @throws ConfigException.UnresolvedSubstitution if a required `${p}` cannot be resolved.
    */
  def resolve(root: sh.Config.Object): sh.Config.Object = {
    val resolver = new Resolver(root)
    resolver.resolveObject(root, Set.empty)
  }

  /** Splits a literal's text into [[Piece]]s. Returns `None` (fast path) when there is no `${`. */
  private def scan(s: String): Option[List[Piece]] = {
    val start = s.indexOf("${")
    if (start < 0) None
    else {
      val pieces = List.newBuilder[Piece]
      var i = 0
      val n = s.length
      while (i < n) {
        val open = s.indexOf("${", i)
        if (open < 0) {
          if (i < n) pieces += Text(s.substring(i))
          i = n
        } else {
          if (open > i) pieces += Text(s.substring(i, open))
          val close = s.indexOf('}', open + 2)
          if (close < 0) {
            // Unterminated `${` — treat the remainder as literal text.
            pieces += Text(s.substring(open))
            i = n
          } else {
            val body = s.substring(open + 2, close)
            val optional = body.startsWith("?")
            val path = (if (optional) body.substring(1) else body).trim
            pieces += Subst(path, optional)
            i = close + 1
          }
        }
      }
      Some(pieces.result())
    }
  }

  private final class Resolver(root: sh.Config.Object) {

    import sh.ConfigOps

    /** Resolve every field of `obj`. Fields whose value is an unresolved optional substitution are
      * dropped (Typesafe semantics: an optional sole value that resolves to nothing removes the key).
      */
    def resolveObject(obj: sh.Config.Object, inProgress: Set[String]): sh.Config.Object = {
      val resolved = obj.fields.iterator.flatMap { case (k, v) =>
        resolveValue(v, inProgress).map(k -> _)
      }.toMap
      sh.Config.Object(resolved)
    }

    /** Resolve a single value. Returns `None` only when the value is an unresolved *optional*
      * substitution that should be omitted entirely.
      */
    private def resolveValue(v: sh.Config.Value, inProgress: Set[String]): Option[sh.Config.Value] =
      v match {
        case o: sh.Config.Object => Some(resolveObject(o, inProgress))
        case sh.Config.Array(elems) =>
          // Array elements are never "omitted": an unresolved optional element becomes an empty
          // string, matching concatenation semantics for a standalone optional reference.
          Some(sh.Config.Array(elems.map(e => resolveValue(e, inProgress).getOrElse(sh.Config.StringLiteral("")))))
        case sh.Config.StringLiteral(s) =>
          scan(s) match {
            case None => Some(v) // no substitution syntax
            case Some(pieces) => resolvePieces(pieces, inProgress)
          }
        case other => Some(other)
      }

    /** Resolve a scanned list of pieces into a single value (or `None` to omit). */
    private def resolvePieces(pieces: List[Piece], inProgress: Set[String]): Option[sh.Config.Value] =
      pieces match {
        // Whole value is a single substitution → preserve the referenced value's type.
        case List(Subst(path, optional)) =>
          lookup(path, optional, inProgress) match {
            case Some(value) => Some(value)
            case None => if (optional) None else throw unresolved(path)
          }
        // Mixed literal text and/or multiple substitutions → string concatenation.
        case _ =>
          val sb = new StringBuilder
          pieces.foreach {
            case Text(t) => sb.append(t)
            case Subst(path, optional) =>
              lookup(path, optional, inProgress) match {
                case Some(value) => sb.append(stringValueOf(value))
                case None => if (!optional) throw unresolved(path) // optional → contributes nothing
              }
          }
          Some(sh.Config.StringLiteral(sb.toString))
      }

    /** Look up `path` in the config root (recursively resolving the found value), then fall back to
      * environment variables. Returns `None` if nothing was found (caller decides required/optional).
      */
    private def lookup(path: String, optional: Boolean, inProgress: Set[String]): Option[sh.Config.Value] = {
      if (inProgress.contains(path)) {
        // Cycle: behave as if unresolved (optional → omit, required → error at the call site).
        None
      } else {
        (root: sh.Config.Value).get(path) match {
          case Some(found) =>
            resolveValue(found, inProgress + path) match {
              case Some(r) => Some(r)
              case None =>
                // The referenced value itself resolved to "omitted" (optional). Treat as not found.
                envFallback(path)
            }
          case None => envFallback(path)
        }
      }
    }

    private def envFallback(path: String): Option[sh.Config.Value] =
      Option(System.getenv(path)).map(sh.Config.StringLiteral.apply)

    /** Render a resolved value as a string for concatenation (numbers/bools → their literal text). */
    private def stringValueOf(v: sh.Config.Value): String =
      v match {
        case sh.Config.StringLiteral(s) => s
        case sh.Config.NumberLiteral(s) => s
        case sh.Config.BooleanLiteral(b) => b.toString
        case sh.Config.NullLiteral => "null"
        case other => ConfigRenderer.render(other, ConfigRenderOptions.defaults())
      }

    private def unresolved(path: String): ConfigException.UnresolvedSubstitution =
      new ConfigException.UnresolvedSubstitution(ConfigOrigin.simple("String"), path)
  }
}
