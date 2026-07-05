package com.typesafe.config

import org.akkajs.{shocon => sh}

class ArrayElementSuite extends munit.FunSuite {

  private def elements(hocon: String): Seq[sh.Config.Value] =
    ConfigFactory.parseString(hocon).root().get("entries").inner match {
      case sh.Config.Array(elems) => elems
      case other => fail(s"expected array, got $other")
    }

  private def field(v: sh.Config.Value, key: String): String =
    v match {
      case sh.Config.Object(fields) =>
        fields(key) match {
          case sh.Config.StringLiteral(s) => s
          case other => other.toString
        }
      case other => fail(s"expected object, got $other")
    }

  test("newline-separated objects in an array stay separate elements") {
    val elems = elements(
      """entries: [
        |  { item: magnet, chance: 100% }
        |  { item: ability_tome, chance: 0% }
        |  { item: power_up, chance: 0% }
        |]""".stripMargin
    )
    assertEquals(elems.size, 3)
    assertEquals(field(elems(0), "item"), "magnet")
    assertEquals(field(elems(0), "chance"), "100%")
    assertEquals(field(elems(1), "item"), "ability_tome")
    assertEquals(field(elems(2), "item"), "power_up")
  }

  test("comma-separated objects in an array stay separate elements") {
    val elems = elements("entries: [ { a: 1 }, { a: 2 }, { a: 3 } ]")
    assertEquals(elems.size, 3)
  }

  test("same-line adjacent objects still concatenate") {
    val elems = elements("entries: [ { a: 1 } { b: 2 } ]")
    assertEquals(elems.size, 1)
    assertEquals(field(elems(0), "a"), "1")
    assertEquals(field(elems(0), "b"), "2")
  }

  test("newline-separated arrays in an array stay separate elements") {
    val elems = elements(
      """entries: [
        |  [1, 2]
        |  [3]
        |]""".stripMargin
    )
    assertEquals(elems.size, 2)
  }
}
