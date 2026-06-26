package com.typesafe

/** A `com.typesafe.config`-shaped value-tree facade for Scala Native, backed by the SHocon
  * runtime parser (`org.akkajs.shocon`).
  *
  * This exists so that `pureconfig-core` — which imports `com.typesafe.config.*` directly and has
  * no backend abstraction — compiles and runs on Scala Native unchanged. It mirrors the *observable*
  * behaviour of Lightbend/Typesafe Config for the subset of the API that pureconfig uses, not its
  * internals.
  *
  * NOTE: this is intentionally NOT the old path-based `facade/` in this repo (which is macro-driven
  * and the wrong shape); it is a separate, value-tree-oriented set of sources compiled into the same
  * single `shocon-parser` Scala Native artifact.
  */
package object config
