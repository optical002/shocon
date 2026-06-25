import scala.scalanative.build._

// ---------------------------------------------------------------------------
// SHocon — Scala-Native-only port (Scala 3).
//
// Upstream SHocon cross-builds JS/JVM and ships a `com.typesafe.config` facade,
// both driven by Scala 2 blackbox macros. This port keeps ONLY the runtime HOCON
// parser (`org.akkajs.shocon`), drops the compile-time macro (it was just an
// optimization that fell back to the same runtime parser), and publishes a single
// Scala Native artifact: `org.akka-js:shocon-parser_native0.5_3`.
//
// Consumed by ../godot-scala-native-utilities (logic-constructor) to parse HOCON
// strings into its own ConfigValue ADT.
// ---------------------------------------------------------------------------

lazy val scala3 = "3.8.1"

ThisBuild / organization := "org.akka-js"
// Fixed (non-SNAPSHOT) version so it resolves cleanly from a raw-git Maven repo
// (no maven-metadata.xml timestamp resolution needed).
ThisBuild / version := "1.0.0-native"
ThisBuild / scalaVersion := scala3

lazy val parser = (project in file("."))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "shocon-parser",
    scalacOptions ++= Seq(
      "-feature",
      "-unchecked",
      "-deprecation",
      "-language:implicitConversions"
    ),
    // Build only the parser sources; the macro loader and the facade are dropped.
    Compile / unmanagedSourceDirectories := Seq(baseDirectory.value / "shared" / "src" / "main" / "scala"),
    Compile / unmanagedSources / excludeFilter :=
      (Compile / unmanagedSources / excludeFilter).value || new SimpleFileFilter(
        _.getName == "ConfigMacroLoader.scala"
      ),
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.12.0",
      "com.lihaoyi" %%% "fastparse" % "3.1.1"
    ),
    publishMavenStyle := true,
    // Publish into a Maven-layout folder committed to this repo's `maven` branch,
    // served raw from GitHub. Consumers add the matching raw.githubusercontent
    // resolver (no auth needed). See README / godot-utilities build.sbt.
    publishTo := Some(
      Resolver.file("github-maven", (ThisBuild / baseDirectory).value / "maven")
    )
  )
