package com.typesafe.config

import java.nio.file.Files

class ResolutionSuite extends munit.FunSuite {

  // --- substitutions -------------------------------------------------------

  test("required substitution resolves to a value defined elsewhere") {
    val c = ConfigFactory.parseString("a = 5\nb = ${a}").resolve()
    // SHocon represents unquoted scalars as StringLiterals (pureconfig coerces on read), so the
    // substituted value mirrors the source value's representation.
    assertEquals(c.root().get("b").unwrapped, "5")
  }

  test("string concatenation with a substitution") {
    val c = ConfigFactory.parseString("""host-suffix = "example.com"
                                         |host = "myhost1."${host-suffix}""".stripMargin).resolve()
    assertEquals(c.root().get("host").unwrapped, "myhost1.example.com")
  }

  test("optional substitution is omitted when missing (whole value → key dropped)") {
    val c = ConfigFactory.parseString("a = ${?missing}\nb = 1").resolve()
    assertEquals(c.root().get("a"), null)
    assertEquals(c.root().get("b").unwrapped, "1")
  }

  test("required substitution that cannot be resolved throws UnresolvedSubstitution") {
    val ex = intercept[ConfigException.UnresolvedSubstitution] {
      ConfigFactory.parseString("""host-override = "myhost1."${host-suffix}""").resolve()
    }
    assertEquals(ex.getMessage, "Could not resolve substitution to a value: ${host-suffix}")
  }

  test("substitution resolves after withFallback merges (deferred resolution)") {
    val resolve1 = ConfigFactory.parseString("""host-override = "myhost1."${host-suffix}""")
    val resolve2 = ConfigFactory.parseString("""host-suffix = "example.com"""")
    val merged = resolve1.withFallback(resolve2).resolve()
    assertEquals(merged.root().get("host-override").unwrapped, "myhost1.example.com")
  }

  test("pureconfig resolve1/resolve2 fixture: cross-config substitution after merge") {
    // Mirrors pureconfig's tests/.../conf/configSource/resolve{1,2}.conf.
    val resolve1 = ConfigFactory.parseString("""host-override = "myhost1."${host-suffix}""")
    val resolve2 = ConfigFactory.parseString(
      """host-suffix = "example.com"
        |host = "myhost2.example.com"
        |host = ${?host-override}""".stripMargin
    )
    val merged = resolve1.withFallback(resolve2).resolve()
    assertEquals(merged.root().get("host").unwrapped, "myhost1.example.com")
  }

  test("environment variable fallback") {
    // PATH is virtually always present; use it as a stable env var to confirm fallback works.
    Option(System.getenv("PATH")) match {
      case Some(p) =>
        val c = ConfigFactory.parseString("x = ${PATH}").resolve()
        assertEquals(c.root().get("x").unwrapped, p)
      case None => () // skip on the rare platform without PATH
    }
  }

  test("recursive substitution (reference to a reference)") {
    val c = ConfigFactory.parseString("a = 1\nb = ${a}\nc = ${b}").resolve()
    assertEquals(c.root().get("c").unwrapped, "1")
  }

  // --- includes ------------------------------------------------------------

  test("include \"b\" merges a sibling object") {
    val dir = Files.createTempDirectory("shocon-include-test")
    val a = dir.resolve("a.conf")
    val b = dir.resolve("b.conf")
    Files.write(b, "conf {\n  a = \"string\"\n}\n".getBytes("UTF-8"))
    Files.write(a, "include \"b\"\n\nconf {\n  b = \"hello\"\n  c = 4\n}\n".getBytes("UTF-8"))

    val c = ConfigFactory.parseFile(a).resolve()
    val conf = c.root().get("conf").asInstanceOf[ConfigObject]
    assertEquals(conf.get("a").unwrapped, "string")
    assertEquals(conf.get("b").unwrapped, "hello")
    assertEquals(conf.get("c").unwrapped, "4")
  }

  test("surrounding keys override included keys") {
    val dir = Files.createTempDirectory("shocon-include-override")
    val a = dir.resolve("a.conf")
    val b = dir.resolve("b.conf")
    Files.write(b, "x = 1\ny = 2\n".getBytes("UTF-8"))
    Files.write(a, "include \"b\"\nx = 99\n".getBytes("UTF-8"))

    val c = ConfigFactory.parseFile(a).resolve()
    assertEquals(c.root().get("x").unwrapped, "99") // local wins
    assertEquals(c.root().get("y").unwrapped, "2") // included survives
  }

  test("missing optional include is silently ignored") {
    val dir = Files.createTempDirectory("shocon-include-missing")
    val a = dir.resolve("a.conf")
    Files.write(a, "include \"nope\"\nx = 1\n".getBytes("UTF-8"))
    val c = ConfigFactory.parseFile(a).resolve()
    assertEquals(c.root().get("x").unwrapped, "1")
  }

  test("required include that is missing throws") {
    val dir = Files.createTempDirectory("shocon-include-required")
    val a = dir.resolve("a.conf")
    Files.write(a, "include required(\"nope\")\nx = 1\n".getBytes("UTF-8"))
    intercept[ConfigException.IO] {
      ConfigFactory.parseFile(a)
    }
  }
}
