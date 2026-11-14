package io.github.tsnee.ears.markdown

import zio.test.*

/** The pure helpers behind the extra-cells warning, on every platform: `Lines`, the source's lines,
  * and `Rows`, which splits a table row's raw text into its cells.
  */
object RowsSuite extends ZIOSpecDefault:

  /** A cell's text, trimmed: letters, spaces, pipes, backslashes, backticks, and ampersands. */
  private val genCell: Gen[Any, String] =
    Gen.stringBounded(0, 8)(Gen.elements('a', 'b', ' ', '|', '\\', '`', '&')).map(_.trim)

  /** Characters that aren't ASCII, written by code point, since scalafmt would turn an escape into
    * the character: a space that isn't a space or tab, and a no-break space.
    */
  private val emSpace = 0x2003.toChar.toString
  private val noBreakSpace = 0x00a0.toChar.toString

  def spec = suite("table helpers")(
    suite("Lines")(
      test("starts: 0, then after each CRLF, CR, or LF"):
        assertTrue(Lines.starts("a\r\nb\rc\nd") == Vector(0, 3, 5, 7))
      ,
      test("of: each line without its ending, the last one too"):
        assertTrue(
          Lines.of("a\r\nb\rc\nd") == Vector("a", "b", "c", "d"),
          Lines.of("a\n") == Vector("a", ""),
          Lines.of("") == Vector("")
        )
    ),
    suite("Rows.cells")(
      test("bare pipes split a row; outer pipes leave no empty cells; cells are trimmed"):
        assertTrue(
          Rows.cells("| a | b |") == List("a", "b"),
          Rows.cells("a | b") == List("a", "b"),
          Rows.cells("  | a |  ") == List("a"),
          Rows.cells("| a | |") == List("a", ""),
          Rows.cells("a") == List("a"),
          Rows.cells("|") == List(""),
          Rows.cells("") == List("")
        )
      ,
      test("cells are trimmed of ASCII whitespace only, as cmark-gfm trims them"):
        val verticalTab = 11.toChar.toString
        assertTrue(
          Rows.cells(s"$verticalTab a \f| b") == List("a", "b"),
          Rows.cells(s"| ${noBreakSpace}a$emSpace | b |") == List(s"${noBreakSpace}a$emSpace", "b")
        )
      ,
      test("a pipe after any run of backslashes stays in its cell, with one backslash fewer"):
        assertTrue(
          Rows.cells("x \\| y") == List("x | y"),
          Rows.cells("x \\\\| y") == List("x \\| y"),
          Rows.cells("x \\\\\\| y | z") == List("x \\\\| y", "z"),
          Rows.cells("x \\ | y") == List("x \\", "y"),
          Rows.cells("| a \\|") == List("a |")
        )
      ,
      test("inline code and character references don't change where a row splits"):
        assertTrue(
          Rows.cells("`a|b` | &#124; c") == List("`a", "b`", "&#124; c"),
          Rows.cells("`a \\| b`") == List("`a | b`")
        )
      ,
      test("a row built from cells, each pipe in them escaped, splits back into those cells"):
        check(Gen.listOfBounded(1, 5)(genCell)) { cells =>
          val row = cells.map(_.replace("|", "\\|")).mkString("| ", " | ", " |")
          assertTrue(Rows.cells(row) == cells)
        }
    )
  )
