// Scala-Native-only port of SHocon. We publish a single `shocon-parser` native
// artifact (the runtime HOCON parser, macro removed). JS/JVM and the
// com.typesafe.config facade are intentionally dropped.
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.10")
