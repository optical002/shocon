package com.typesafe.config
package impl

import java.nio.file.{Files, Path, Paths}

import scala.util.control.NonFatal

import org.akkajs.{shocon => sh}

/** Expands HOCON `include` directives, which the SHocon fastparse grammar cannot represent.
  *
  * Strategy: a line-oriented preprocess over the raw config text. Each `include` line is removed and
  * the referenced object is parsed and merged — as a *fallback* (surrounding keys win) — at the
  * brace-nesting level where the directive appeared. The remaining (include-free) text is parsed by
  * SHocon as usual.
  *
  * Supported forms:
  *   - `include "name"`            — heuristic: file relative to the including file's directory; on
  *                                   Native, classpath ≈ file relative to the working directory.
  *   - `include file("path")`      — explicit file.
  *   - `include classpath("res")`  — classpath resource (file relative to cwd on Native).
  *   - `include url("file:...")`   — file: URLs only (see [[ConfigFactory.parseURL]]).
  *   - `include required(<any>)`   — missing target is an error; otherwise missing is silently empty.
  *
  * Brace nesting is tracked well enough for top-level and one-level-deep includes (the common
  * cases). Includes nested inside list values are not supported.
  */
private[config] object IncludeResolver {

  /** Parse `text`, expanding any includes relative to `baseDir`. */
  def parse(text: String, baseDir: Option[Path], origin: ConfigOrigin): sh.Config.Object = {
    val (stripped, includes) = extractIncludes(text)
    val base = parseRaw(stripped, origin)
    // Merge each included object (nested under its path) as a fallback beneath the local object.
    includes.foldLeft(base) { case (acc, inc) =>
      loadInclude(inc, baseDir) match {
        case Some(obj) =>
          val nested = nestUnder(inc.path, obj)
          // acc wins over included content (HOCON: surrounding keys override the include).
          sh.Config.Object.mergeConfigs(nested, acc)
        case None => acc
      }
    }
  }

  private def parseRaw(text: String, origin: ConfigOrigin): sh.Config.Object =
    // Quote-wrap unquoted `${...}` so the SHocon grammar accepts it; the resolver re-scans later.
    sh.Config(SubstitutionPreprocessor.rewrite(text)) match {
      case o: sh.Config.Object => o
      case other => sh.Config.Object(Map("" -> other))
    }

  /** An include directive captured during preprocessing. */
  private final case class Include(kind: Kind, name: String, required: Boolean, path: List[String])

  private sealed trait Kind
  private case object Heuristic extends Kind
  private case object FileKind extends Kind
  private case object Classpath extends Kind
  private case object UrlKind extends Kind

  /** Remove `include` lines from `text`, returning the cleaned text and the directives found (each
    * tagged with the object path it sat under). Brace nesting is tracked from `key {` openers.
    */
  private def extractIncludes(text: String): (String, List[Include]) = {
    val out = new StringBuilder
    val found = List.newBuilder[Include]
    var pathStack: List[String] = Nil

    text.linesIterator.foreach { rawLine =>
      val line = rawLine.trim
      parseIncludeLine(line) match {
        case Some((kind, name, required)) =>
          found += Include(kind, name, required, pathStack.reverse)
        // drop the line from the output
        case None =>
          // Track nesting: a line like `key {` (with nothing after the brace) pushes `key`; a line
          // that is just `}` pops. This is deliberately simple — sufficient for the supported depth.
          val openMatch = """^([^=:{}\[\]"]+?)\s*\{\s*$""".r
          line match {
            case openMatch(key) => pathStack = key.trim :: pathStack
            case "}" => pathStack = pathStack.drop(1)
            case _ => ()
          }
          out.append(rawLine).append('\n')
      }
    }
    (out.toString, found.result())
  }

  /** Parse an `include ...` line into (kind, name, required), or `None` if it isn't an include. */
  private def parseIncludeLine(line: String): Option[(Kind, String, Boolean)] = {
    if (!line.startsWith("include")) None
    else {
      val rest = line.substring("include".length).trim
      if (rest.isEmpty) None
      else {
        val (required, body) =
          if (rest.startsWith("required(") && rest.endsWith(")"))
            (true, rest.substring("required(".length, rest.length - 1).trim)
          else (false, rest)
        parseSource(body).map { case (kind, name) => (kind, name, required) }
      }
    }
  }

  private def parseSource(body: String): Option[(Kind, String)] = {
    def inner(prefix: String, kind: Kind): Option[(Kind, String)] =
      if (body.startsWith(prefix) && body.endsWith(")"))
        Some(kind -> unquote(body.substring(prefix.length, body.length - 1).trim))
      else None

    inner("file(", FileKind)
      .orElse(inner("classpath(", Classpath))
      .orElse(inner("url(", UrlKind))
      .orElse(if (body.startsWith("\"")) Some(Heuristic -> unquote(body)) else None)
  }

  private def unquote(s: String): String = {
    val t = s.trim
    if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) t.substring(1, t.length - 1) else t
  }

  /** Nest `obj` under `path` (e.g. path `["conf"]` → `{ conf = obj }`). Empty path → `obj`. */
  private def nestUnder(path: List[String], obj: sh.Config.Object): sh.Config.Object =
    path.foldRight(obj) { (key, acc) => sh.Config.Object(Map(key -> acc)) }

  /** Load and parse an include target. `None` when missing and not required. */
  private def loadInclude(inc: Include, baseDir: Option[Path]): Option[sh.Config.Object] = {
    val candidates: List[Path] = inc.kind match {
      case UrlKind =>
        // file: URLs only.
        if (inc.name.startsWith("file:")) List(Paths.get(inc.name.substring("file:".length)))
        else
          if (inc.required)
            throw new ConfigException.IO(
              ConfigOrigin.forUrl(inc.name),
              s"Reading non-file URLs is unsupported on Scala Native: ${inc.name}"
            )
          else Nil
      case _ =>
        // Heuristic / file / classpath all resolve to a filesystem path on Native. Relative names
        // are resolved against the including file's directory; absolute names are kept.
        val raw = Paths.get(inc.name)
        val resolved =
          if (raw.isAbsolute) raw
          else baseDir.map(_.resolve(inc.name)).getOrElse(raw)
        withExtensions(resolved)
    }

    candidates.find(Files.exists(_)) match {
      case Some(p) => Some(readAndParse(p))
      case None =>
        if (inc.required)
          throw new ConfigException.IO(
            ConfigOrigin.simple("include"),
            s"required include not found: ${inc.name}"
          )
        else None
    }
  }

  /** If `path` already has an extension, just that; otherwise try `.conf`, `.json`, then bare. */
  private def withExtensions(path: Path): List[Path] = {
    val name = path.getFileName.toString
    if (name.contains(".")) List(path)
    else {
      val parent = Option(path.getParent)
      def sibling(n: String): Path = parent.map(_.resolve(n)).getOrElse(Paths.get(n))
      List(sibling(name + ".conf"), sibling(name + ".json"), path)
    }
  }

  private def readAndParse(path: Path): sh.Config.Object = {
    val origin = ConfigOrigin.forFile(path.toString)
    val text =
      try new String(Files.readAllBytes(path), "UTF-8")
      catch { case NonFatal(e) => throw new ConfigException.IO(origin, e.getMessage, e) }
    // Recurse so the included file's own includes are expanded relative to its directory.
    try parse(text, Option(path.toAbsolutePath.getParent), origin)
    catch {
      case e: ConfigException => throw e
      case NonFatal(e) => throw new ConfigException.Parse(origin, e.getMessage, e)
    }
  }
}
