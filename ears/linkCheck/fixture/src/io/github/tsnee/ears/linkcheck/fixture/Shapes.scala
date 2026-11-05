package io.github.tsnee.ears.linkcheck.fixture

/** Every shape of public operation the link-check generator says it can reference.
  * `scripts/check-scalajs-link` generates a program from this module's SemanticDB and compiles it
  * with these sources, so a shape that `LinkCheck` handles wrongly fails to compile there. Not
  * published; nothing calls these.
  */
object Shapes:

  def plain(text: String): Int = text.length
  def empty(): Int = 0
  def emptyThenMore()(text: String): Int = text.length
  def curried(a: Int)(b: Int): Int = a + b
  def byName(value: => Int): Int = value
  def generic[A](value: A): List[A] = List(value)
  inline def inlined(value: Int): Int = value + 1
  def parameterless: Int = 1
  val limit: Int = 40
  lazy val lazyLimit: Int = 41
  def `+++`(text: String): String = text + text
  @deprecated("use plain", "0.1.1") def old(text: String): Int = text.length

  given ordering: Ordering[Box] = Ordering.by(_.size)

  extension (box: Box) def doubled: Box = Box(box.size * 2)

  object Nested:
    def inside(value: Int): Int = value

/** A class with operations of its own. */
final class Box(val size: Int):
  def grow(by: Int): Box = Box(size + by)
  def reset(): Box = Box(0)
  def area: Int = size * size

  /** A class nested in a class. */
  final class Lid(val tight: Boolean):
    def open: Lid = Lid(false)

/** A generic class. */
final class Pair[A, B](val first: A, val second: B):
  def swap: Pair[B, A] = Pair(second, first)
