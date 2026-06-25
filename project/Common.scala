import sbt._

// Settings now live in build.sbt for the Scala-Native-only port; this AutoPlugin
// is intentionally a no-op (previously it pinned Scala 2.13 + Sonatype publish).
object Common extends AutoPlugin {
  override def trigger = noTrigger
}
