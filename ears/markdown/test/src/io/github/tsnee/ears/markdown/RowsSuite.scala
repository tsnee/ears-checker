package io.github.tsnee.ears.markdown

import zio.test.*

/** The pure helpers behind tables, on every platform: `Lines`, the source's lines, and `Rows`,
  * which reads a table row's raw text: its container markers, its cells, and whether it's a
  * delimiter row.
  */
object RowsSuite extends ZIOSpecDefault:

  /** A cell's text, trimmed: letters, spaces, pipes, backslashes, backticks, and ampersands. */
  private val genCell: Gen[Any, String] =
    Gen.stringBounded(0, 8)(Gen.elements('a', 'b', ' ', '|', '\\', '`', '&')).map(_.trim)

  /** Characters that aren't ASCII, written by code point, since scalafmt would turn an escape into
    * the character: a digit that isn't ASCII, a space that isn't a space or tab, and a no-break
    * space.
    */
  private val arabicThree = 0x0663.toChar.toString
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
    suite("Rows.stripped")(
      test("leading whitespace and block-quote markers, repeated, on any line"):
        assertTrue(
          Rows.stripped("  > >| a |", first = false) == "| a |",
          Rows.stripped(">\t> | a |", first = true) == "| a |",
          Rows.stripped("    | a |", first = false) == "| a |"
        )
      ,
      test("list markers, repeated and mixed with block quotes, on the first line only"):
        assertTrue(
          Rows.stripped("- 1. > 2) + * | a |", first = true) == "| a |",
          Rows.stripped("> 123456789.\t| a |", first = true) == "| a |",
          Rows.stripped("- | a |", first = false) == "- | a |",
          Rows.stripped("  1. | a |", first = false) == "1. | a |"
        )
      ,
      test("a marker needs whitespace after it, and at most nine digits"):
        assertTrue(
          Rows.stripped("-x | a", first = true) == "-x | a",
          Rows.stripped("1.5 | a", first = true) == "1.5 | a",
          Rows.stripped("1234567890. a", first = true) == "1234567890. a",
          Rows.stripped("1x a", first = true) == "1x a",
          Rows.stripped("12", first = true) == "12",
          Rows.stripped("-", first = true) == "-",
          Rows.stripped("\\- a", first = true) == "\\- a"
        )
      ,
      test("a line of only markers and whitespace is left empty"):
        assertTrue(Rows.stripped(" > > ", first = false) == "")
      ,
      test("a marker's number is ASCII digits; whitespace in a prefix is a space or a tab"):
        assertTrue(
          Rows.stripped(s"$arabicThree. a", first = true) == s"$arabicThree. a",
          Rows.stripped(s"$emSpace> a", first = false) == s"$emSpace> a",
          Rows.stripped(s"-${emSpace}a", first = true) == s"-${emSpace}a"
        )
    ),
    suite("Rows.opened")(
      test("a line with no container"):
        assertTrue(
          Rows.opened("| a |") ==
            (Nil -> RowLine(text = "| a |", inner = "| a |", isLazy = false, indent = 0)),
          Rows.opened("  | a |") ==
            (Nil -> RowLine(text = "| a |", inner = "  | a |", isLazy = false, indent = 2))
        )
      ,
      test("block quotes and list items, nested, each item as wide as its marker and spaces"):
        assertTrue(
          Rows.opened("  > - 12) text") == (
            List(Container.Quote, Container.Item(2), Container.Item(4)) ->
              RowLine(text = "text", inner = "text", isLazy = false, indent = 0)
          ),
          Rows.opened("-\tx") ==
            (List(Container.Item(4)) -> RowLine(
              text = "x",
              inner = "x",
              isLazy = false,
              indent = 0
            ))
        )
      ,
      test("four or more columns of indentation belong to an item opened earlier"):
        assertTrue(
          Rows.opened("    > x") == (
            List(Container.Item(4), Container.Quote) ->
              RowLine(text = "x", inner = "x", isLazy = false, indent = 0)
          ),
          Rows.opened("-     x") == (
            List(Container.Item(2), Container.Item(4)) ->
              RowLine(text = "x", inner = "x", isLazy = false, indent = 0)
          )
        )
      ,
      test("a tab after a block quote's marker counts one column as the marker's space"):
        assertTrue(
          Rows.opened(">\t| a |") == (
            List(Container.Quote) ->
              RowLine(text = "| a |", inner = "  | a |", isLazy = false, indent = 2)
          )
        )
    ),
    suite("Rows.matched")(
      test("a line that matches its containers, without their prefix"):
        val containers = List(Container.Quote, Container.Item(2))
        assertTrue(
          Rows.matched(">   | x |", containers) ==
            RowLine(text = "| x |", inner = "| x |", isLazy = false, indent = 0),
          Rows.matched(">       x", containers) ==
            RowLine(text = "x", inner = "    x", isLazy = false, indent = 4),
          Rows.matched("     x", Nil) ==
            RowLine(text = "x", inner = "     x", isLazy = false, indent = 5)
        )
      ,
      test("a line that doesn't match them all is lazy"):
        val containers = List(Container.Quote, Container.Item(2))
        assertTrue(
          Rows.matched("| x |", containers) ==
            RowLine(text = "| x |", inner = "| x |", isLazy = true, indent = 0),
          Rows.matched(">  x", containers) ==
            RowLine(text = "x", inner = " x", isLazy = true, indent = 1),
          Rows.matched("    > x", List(Container.Quote)).isLazy
        )
    ),
    suite("Rows.endsTable")(
      test("a lazy line, a code line, or a line that starts a block ends a table"):
        val row = (text: String) => RowLine(text = text, inner = text, isLazy = false, indent = 0)
        assertTrue(
          Rows.endsTable(row("| x |").copy(isLazy = true)),
          Rows.endsTable(row("| x |").copy(indent = 4)),
          Rows.endsTable(row("2. x | y")),
          !Rows.endsTable(row("| x |").copy(indent = 3))
        )
    ),
    suite("Rows.startsBlock")(
      test("a list marker, an empty item's too, with ASCII digits"):
        assertTrue(
          List("-", "+ a", "*\tb", "1. a", "123456789) a", "1.").forall(Rows.startsBlock),
          !List("-a", "1234567890. a", s"$arabicThree. a", "1.5", "**a**", "| - |", s"-${emSpace}a")
            .exists(Rows.startsBlock)
        )
      ,
      test("the start of each of CommonMark's seven kinds of HTML block"):
        assertTrue(
          List(
            "<script>",
            "<PRE class=x>",
            "<style",
            "<textarea",
            "<!-- note",
            "<?php",
            "<!DOCTYPE html>",
            "<![CDATA[x",
            "<div>",
            "</DIV>",
            "<hr/>",
            "<p",
            "<span>",
            "<span class=\"note\">  ",
            "</span>",
            "<a href='x' title=y data-x>",
            "<x-y/>"
          ).forall(Rows.startsBlock)
        )
      ,
      test("an inline tag with text after it, or what isn't a tag, starts no block"):
        assertTrue(
          !List("<span> text", "<divx", "< div>", "a <span>", "<a href=>", "<1a>", "<span")
            .exists(Rows.startsBlock)
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
    ),
    suite("Rows.isDelimiter")(
      test("cells of dashes, each with an optional colon at either end, and a pipe"):
        assertTrue(
          Rows.isDelimiter("|---|:--:|"),
          Rows.isDelimiter("--- | ---"),
          Rows.isDelimiter("| :- |"),
          Rows.isDelimiter("-|")
        )
      ,
      test("no pipe, another character, an empty cell, or an escaped pipe: no delimiter row"):
        assertTrue(
          !Rows.isDelimiter("---"),
          !Rows.isDelimiter("|---|x|"),
          !Rows.isDelimiter("|---||"),
          !Rows.isDelimiter("|"),
          !Rows.isDelimiter("--- \\| ---"),
          !Rows.isDelimiter("|- -|"),
          !Rows.isDelimiter("|::|")
        )
    )
  )
