package com.typesafe.config

/** Marker supertype of `ConfigValue`/`Config`, mirroring `com.typesafe.config.ConfigMergeable`.
  * pureconfig does not call its methods, so the trait is intentionally empty.
  */
trait ConfigMergeable
