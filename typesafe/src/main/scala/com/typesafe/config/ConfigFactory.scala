package com.typesafe.config

import java.io.File
import java.net.URL
import java.nio.file.{Files, Path, Paths}

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import org.akkajs.{shocon => sh}

/** Mirrors the subset of `com.typesafe.config.ConfigFactory` pureconfig uses.
  *
  * Native limitations: there is no JVM classloader to enumerate `reference.conf` resources across a
  * classpath, so the "default" loaders fall back to optional files in the working directory and
  * system properties. Explicit string/file/URL sources work fully. See the port plan.
  */
object ConfigFactory {

  private val stringOrigin = ConfigOrigin.simple("String")

  def empty(): Config = Config.empty()

  def invalidateCaches(): Unit = ()

  // --- explicit parsing (fully supported on Native) ---

  def parseString(s: String): Config =
    try Config(sh.Config(s), stringOrigin)
    catch {
      case NonFatal(e) => throw new ConfigException.Parse(stringOrigin, e.getMessage, e)
    }

  def parseFile(file: File): Config = parseFile(file, ConfigParseOptions.defaults)

  def parseFile(file: File, options: ConfigParseOptions): Config =
    parsePath(file.toPath, options)

  def parseFile(path: Path): Config = parseFile(path, ConfigParseOptions.defaults)

  def parseFile(path: Path, options: ConfigParseOptions): Config =
    parsePath(path, options)

  def parseURL(url: URL): Config = parseURL(url, ConfigParseOptions.defaults)

  def parseURL(url: URL, options: ConfigParseOptions): Config = {
    val origin = ConfigOrigin.forUrl(url.toString)
    if (url.getProtocol == "file") parsePath(Paths.get(url.getPath), options)
    else
      throw new ConfigException.IO(origin, s"Reading non-file URLs is unsupported on Scala Native: $url")
  }

  def parseResources(resource: String): Config =
    parseResources(resource, ConfigParseOptions.defaults)

  def parseResources(resource: String, options: ConfigParseOptions): Config = {
    // No classpath on Native: resolve the resource name as a file relative to the working dir.
    val path = Paths.get(resource)
    parsePath(path, options, resourceName = Some(resource))
  }

  // --- "default" loading (Native-limited) ---

  def defaultOverrides(): Config = systemProperties()

  def defaultApplication(): Config = optionalFile("application.conf")

  def defaultReference(): Config = empty()

  def defaultReference(cl: ClassLoader): Config = defaultReference()

  def load(): Config =
    defaultOverrides().withFallback(defaultApplication()).withFallback(defaultReference())

  def load(cl: ClassLoader): Config = load()

  def load(config: Config): Config =
    defaultOverrides().withFallback(config).withFallback(defaultReference())

  def systemProperties(): Config = {
    val props = System.getProperties
    val fields = props.stringPropertyNames().asScala.iterator.map { k =>
      k -> (sh.Config.StringLiteral(props.getProperty(k)): sh.Config.Value)
    }.toMap
    Config(sh.Config.Object(fields), ConfigOrigin.simple("system properties"))
  }

  // --- helpers ---

  private def parsePath(
      path: Path,
      options: ConfigParseOptions,
      resourceName: Option[String] = None
  ): Config = {
    val origin = ConfigOrigin.forFile(path.toString)
    if (!Files.exists(path)) {
      if (options.allowMissing) empty(origin)
      else
        throw new ConfigException.IO(
          origin,
          resourceName.map(r => s"resource not found: $r").getOrElse(s"file not found: $path")
        )
    } else {
      val text =
        try new String(Files.readAllBytes(path), "UTF-8")
        catch { case NonFatal(e) => throw new ConfigException.IO(origin, e.getMessage, e) }
      try Config(sh.Config(text), origin)
      catch { case NonFatal(e) => throw new ConfigException.Parse(origin, e.getMessage, e) }
    }
  }

  private def optionalFile(name: String): Config = {
    val path = Paths.get(name)
    if (Files.exists(path)) parsePath(path, ConfigParseOptions.defaults.setAllowMissing(false))
    else empty(ConfigOrigin.simple(name))
  }

  private def empty(origin: ConfigOrigin): Config = Config.empty(origin)
}
