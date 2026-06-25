# shocon — raw-git Maven repo

This branch hosts the published Scala-Native build of the SHocon HOCON parser as a
plain Maven layout, served directly from GitHub (no auth needed to consume).

Source lives on the [`scala3-native-port`](../../tree/scala3-native-port) branch.

## Consume it

```scala
// resolver
resolvers += "shocon-native" at
  "https://raw.githubusercontent.com/optical002/shocon/maven/maven"

// dependency (Scala Native, Scala 3)
libraryDependencies += "org.akka-js" %%% "shocon-parser" % "1.0.0-native"
```

Published artifact: `org.akka-js:shocon-parser_native0.5_3:1.0.0-native`

## Re-publish (maintainer)

From the source branch:

```bash
git checkout scala3-native-port
sbt parser/publish          # writes to ./maven/
# copy ./maven into this branch's worktree and commit
```
