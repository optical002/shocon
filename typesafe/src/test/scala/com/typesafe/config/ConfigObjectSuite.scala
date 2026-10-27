package com.typesafe.config

import scala.jdk.CollectionConverters._

class ConfigObjectSuite extends munit.FunSuite {

  private val root = ConfigFactory.parseString(
    """a: "x"
      |b: { c: "y", d: [1, 2] }
      |e: [3]""".stripMargin
  ).root()

  test("map lookups read the backing fields") {
    assertEquals(root.size(), 3)
    assert(!root.isEmpty())
    assert(root.containsKey("a"))
    assert(root.containsKey("e"))
    assert(!root.containsKey("missing"))
    assert(!root.containsKey(1))
    assertEquals(root.keySet().asScala.toSet, Set("a", "b", "e"))
  }

  test("entrySet iterates every field with wrapped values") {
    val entries = root.entrySet().asScala.map(e => e.getKey -> e.getValue.valueType()).toMap
    assertEquals(root.entrySet().size(), 3)
    assertEquals(
      entries,
      Map(
        "a" -> ConfigValueType.STRING,
        "b" -> ConfigValueType.OBJECT,
        "e" -> ConfigValueType.LIST
      )
    )
  }

  test("entrySet and key checks walk a deep tree") {
    val depth = 300
    val text = (1 to depth).map(i => s"k$i: { leaf$i: \"v\"\n").mkString + ("}\n" * depth)
    var node: ConfigValue = ConfigFactory.parseString(text).root()
    var level = 0
    while (node.isInstanceOf[ConfigObject]) {
      val obj = node.asInstanceOf[ConfigObject]
      if (level > 0) assert(obj.containsKey(s"leaf$level"))
      node = obj.entrySet().asScala.find(_.getKey.startsWith("k")).map(_.getValue).orNull
      level += 1
    }
    assertEquals(level, depth + 1)
  }
}
