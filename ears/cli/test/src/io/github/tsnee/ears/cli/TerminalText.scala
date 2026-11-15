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

  /** Whether a code unit is in the English character set every output keeps: printable ASCII,
    * Latin-1 without the soft hyphen, the combining accents, and the typographic punctuation word
    * processors insert. Written out here, apart from `Visible`, so a test can't share its mistake.
    */
  def english(c: Char): Boolean =
    List(
      0x20 -> 0x7e,
      0xa0 -> 0xac,
      0xae -> 0xff,
      0x300 -> 0x36f,
      0x2010 -> 0x2022,
      0x2026 -> 0x2026,
      0x2030 -> 0x2030,
      0x2032 -> 0x2033,
      0x2039 -> 0x203a,
      0x20ac -> 0x20ac,
      0x2122 -> 0x2122
    ).exists((low, high) => c.toInt >= low && c.toInt <= high)

  /** Up to 20 of those characters. */
  val texts: Gen[Any, String] = Gen.listOfBounded(0, 20)(characters).map(_.mkString)
