package io.github.tsnee.ears.cli

import zio.test.*

/** Text holding characters a terminal or a line-based tool could act on, for the properties of
  * what's escaped in output (`Visible`, `Checker`) and in log lines (`Logging`).
  */
object TerminalText:

  /** A character a terminal or a line-based tool could act on: C0, DEL, C1, and Unicode's line and
    * paragraph separators.
    */
  def acted(c: Char): Boolean =
    Character.isISOControl(c) || c.toInt == 0x2028 || c.toInt == 0x2029

  /** Every acted-on character and nothing else: C0, DEL and C1, and Unicode's line and paragraph
    * separators.
    */
  val controls: Gen[Any, Char] = Gen.oneOf(
    Gen.char('\u0000', '\u001f'),
    Gen.char('\u007f', '\u009f'),
    Gen.elements('\u2028', '\u2029')
  )

  /** Characters mixing the acted-on kinds (C0, DEL, C1, the separators) with plain text,
    * backslashes, and a no-break space.
    */
  val characters: Gen[Any, Char] = Gen.oneOf(
    Gen.char('\u0000', '\u001f'),
    Gen.char('\u007f', '\u009f'),
    Gen.elements('\u2028', '\u2029', '\\', 'u', '0', 'é', '\u00a0'),
    Gen.char(' ', '~')
  )

  /** Up to 20 of those characters. */
  val texts: Gen[Any, String] = Gen.listOfBounded(0, 20)(characters).map(_.mkString)
