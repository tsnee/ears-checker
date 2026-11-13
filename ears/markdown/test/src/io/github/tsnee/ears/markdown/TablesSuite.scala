package io.github.tsnee.ears.markdown

import zio.test.*

/** `Tables`' steps on their own, with a fake parser where one is needed, on every platform; the
  * conformance suite reads whole documents with each platform's parser.
  */
object TablesSuite extends ZIOSpecDefault:

  private val break = Piece(" ", code = false, line = None)

  private def text(value: String, line: Int) = Piece(value, code = false, line = Some(line))

  private def source(lines: String*): Int => String = line => lines.lift(line - 1).getOrElse("")

  /** A parser that must not be called. */
  private val unused: String => Either[String, List[ParsedParagraph]] =
    document => Left(s"parsed again: $document")

  def spec = suite("Tables")(
    suite("layout")(
      test("a paragraph with no delimiter row has no layout"):
        assertTrue(
          Tables.layout(block = 1, first = 1, lastLine = 2, source("a | b", "c | d")).isEmpty
        )
      ,
      test("lines with a pipe and a dash but no delimiter row have no layout"):
        assertTrue(
          Tables.layout(block = 1, first = 1, lastLine = 2, source("a | b", "c | d-e")).isEmpty
        )
      ,
      test("the lines before the table, then each nonempty cell, on its row's line"):
        val lines = source("> - Intro", ">   more", ">   | A | B |", ">   |---|---|", ">   | | x |")
        assertTrue(
          Tables.layout(block = 1, first = 1, lastLine = 5, lines) == Some(
            TableLayout(
              List(
                Segment(List("Intro", "more"), 1),
                Segment(List("A"), 3),
                Segment(List("B"), 3),
                Segment(List("x"), 5)
              ),
              Nil,
              after = None
            )
          )
        )
      ,
      test("the paragraph starts after the definitions that open its block"):
        val lines = source("[r]: /u", "| A |", "|---|", "| x |")
        val definitions: String => Either[String, List[ParsedParagraph]] =
          document => Either.cond(document == "[r]: /u\n", Nil, s"parsed: $document")
        val segments = (paragraph: Paragraph) =>
          Tables
            .firstLine(paragraph, lines, definitions)
            .map(first => Tables.layout(paragraph.line, first, 4, lines).map(_.segments))
        assertTrue(
          segments(Paragraph(1, List(text("| A |", 2)))) ==
            Right(Some(List(Segment(List("A"), 2), Segment(List("x"), 4)))),
          segments(Paragraph(2, Nil)) ==
            Right(Some(List(Segment(List("A"), 2), Segment(List("x"), 4))))
        )
      ,
      test("a body row's cells beyond the header's are left out, with a warning"):
        val lines = source("| A |", "|---|", "| x | y | z |", "| w |")
        assertTrue(
          Tables.layout(block = 1, first = 1, lastLine = 4, lines) == Some(
            TableLayout(
              List(Segment(List("A"), 1), Segment(List("x"), 3), Segment(List("w"), 4)),
              List(MarkdownWarning.ExtraCells(line = 3, cells = 3, headerCells = 1)),
              after = None
            )
          )
        )
    ),
    suite("document")(
      test("each segment a paragraph after `x `, later lines indented, then the original"):
        assertTrue(
          Tables.document(
            List(Segment(List("Intro `a", "- b"), 1), Segment(List("c"), 4)),
            "original\n"
          ) == "x Intro `a\n    - b\n\nx c\n\noriginal\n"
        )
    ),
    suite("placed")(
      test("each paragraph takes its segment's lines, without the `x `"):
        val segments = List(Segment(List("a", "b"), 3), Segment(List("c"), 7))
        val parsed = List(
          Paragraph(1, List(text("x a", 1), break, text("b", 2))),
          Paragraph(4, List(text("x ", 4), Piece("c", code = true, line = Some(4)))),
          Paragraph(6, List(text("x original", 6)))
        )
        assertTrue(
          Tables.placed(segments, parsed) == List(
            Paragraph(3, List(text("a", 3), break, text("b", 4))),
            Paragraph(7, List(Piece("c", code = true, line = Some(7))))
          )
        )
      ,
      test("the `x ` is removed across text pieces; a paragraph without pieces stays empty"):
        val segments = List(Segment(List("c"), 2), Segment(List("d"), 3))
        val parsed = List(Paragraph(1, List(text("x", 1), text(" c", 1))), Paragraph(3, Nil))
        assertTrue(
          Tables.placed(segments, parsed) ==
            List(Paragraph(2, List(text("c", 2))), Paragraph(3, Nil))
        )
    ),
    suite("read")(
      test("a document without a table isn't parsed again"):
        val paragraph = Paragraph(1, List(text("The scheduler shall list.", 1)))
        assertTrue(
          Tables.read(
            List(ParsedParagraph(paragraph, lastLine = 1, underlined = false)),
            "The scheduler shall list.\n",
            unused
          ) ==
            Right(TableBlocks(List(paragraph), Nil))
        )
      ,
      test("a table whose cells are all empty isn't parsed again, and gives nothing"):
        val content = "| |\n|---|\n"
        assertTrue(
          Tables.read(
            List(ParsedParagraph(Paragraph(1, Nil), lastLine = 2, underlined = false)),
            content,
            unused
          ) ==
            Right(TableBlocks(Nil, Nil))
        )
      ,
      test("tables' segments are parsed once, in one document, and replace their paragraphs"):
        val content = "Intro.\n\n| A |\n|---|\n| x | y |\n"
        val expected = "x A\n\nx x\n\n"
        val parse: String => Either[String, List[ParsedParagraph]] = document =>
          Either.cond(
            document == expected,
            List(
              ParsedParagraph(Paragraph(1, List(text("x A", 1))), lastLine = 1, underlined = false),
              ParsedParagraph(Paragraph(3, List(text("x x", 3))), lastLine = 3, underlined = false)
            ),
            document
          )
        val intro = Paragraph(1, List(text("Intro.", 1)))
        assertTrue(
          Tables.read(
            List(
              ParsedParagraph(intro, lastLine = 1, underlined = false),
              ParsedParagraph(Paragraph(3, Nil), lastLine = 5, underlined = false)
            ),
            content,
            parse
          ) == Right(
            TableBlocks(
              List(intro, Paragraph(3, List(text("A", 3))), Paragraph(5, List(text("x", 5)))),
              List(MarkdownWarning.ExtraCells(line = 5, cells = 2, headerCells = 1))
            )
          )
        )
      ,
      test("the parser's failure is the read's"):
        val content = "| A |\n|---|\n"
        assertTrue(
          Tables.read(
            List(ParsedParagraph(Paragraph(1, Nil), lastLine = 2, underlined = false)),
            content,
            unused
          ) ==
            Left("parsed again: x A\n\n")
        )
      ,
      test("when a segment holds a `[`, the original follows the segments, for its definitions"):
        val content = "| A |\n|---|\n| [x][s] |\n\n[s]: /u\n"
        assertTrue(
          Tables.read(
            List(ParsedParagraph(Paragraph(1, Nil), lastLine = 3, underlined = false)),
            content,
            unused
          ) ==
            Left("parsed again: x A\n\nx [x][s]\n\n" + content)
        )
      ,
      test("a document with pipes but no line with a pipe and a dash after another isn't parsed"):
        val paragraph = Paragraph(1, List(text("a | b", 1)))
        val dash = Paragraph(3, List(text("c - d", 3)))
        assertTrue(
          Tables.read(
            List(
              ParsedParagraph(paragraph, lastLine = 1, underlined = false),
              ParsedParagraph(dash, lastLine = 3, underlined = false)
            ),
            "a | b\n\nc - d\n",
            unused
          ) == Right(TableBlocks(List(paragraph, dash), Nil))
        )
      ,
      test("finding a paragraph's first line, the parser's failure is the read's"):
        val content = "![](a.png)\n| A |\n|---|\n"
        assertTrue(
          Tables
            .read(
              List(
                ParsedParagraph(
                  Paragraph(1, List(text("| A |", 2))),
                  lastLine = 3,
                  underlined = false
                )
              ),
              content,
              unused
            )
            == Left("parsed again: ![](a.png)\n")
        )
      ,
      test("the lines after a table's end are parsed as a document of their own, on their lines"):
        val content = "| A |\n|---|\n- x | y\n"
        val parse: String => Either[String, List[ParsedParagraph]] = {
          case "x A\n\n" =>
            Right(
              List(
                ParsedParagraph(
                  Paragraph(1, List(text("x A", 1))),
                  lastLine = 1,
                  underlined = false
                )
              )
            )
          case "- x | y\n" =>
            Right(
              List(
                ParsedParagraph(
                  Paragraph(1, List(text("x | y", 1))),
                  lastLine = 1,
                  underlined = false
                )
              )
            )
          case document => Left(document)
        }
        assertTrue(
          Tables.read(
            List(ParsedParagraph(Paragraph(1, Nil), lastLine = 3, underlined = false)),
            content,
            parse
          ) ==
            Right(
              TableBlocks(
                List(Paragraph(1, List(text("A", 1))), Paragraph(3, List(text("x | y", 3)))),
                Nil
              )
            )
        )
      ,
      test("the lines after a table's end, holding a `[`, are followed by the original"):
        val content = "| A |\n|---|\n- [x][s]\n\n[s]: /u\n"
        val parse: String => Either[String, List[ParsedParagraph]] = {
          case "x A\n\n" => Right(Nil)
          case document  => Left(document)
        }
        assertTrue(
          Tables.read(
            List(ParsedParagraph(Paragraph(1, Nil), lastLine = 3, underlined = false)),
            content,
            parse
          ) ==
            Left("- [x][s]\n\n" + content)
        )
    ),
    suite("underline")(
      test("the underline before a reported end one line late, after a paragraph or a blank"):
        assertTrue(
          Tables.underline(
            block = 1,
            first = 1,
            reported = 4,
            source("| A |", "|---|", "===", "next")
          ) == 3,
          Tables.underline(
            block = 1,
            first = 1,
            reported = 4,
            source("| A |", "|---|", "---", "")
          ) == 3,
          Tables.underline(block = 2, first = 2, reported = 3, source("", "Title", "-", "")) == 3
        )
      ,
      test("in a container, past its prefix, at most three spaces in, with spaces after"):
        val lines = source("> - | A |", ">   |---|", ">      ===  ", "> next")
        assertTrue(Tables.underline(block = 1, first = 1, reported = 4, lines) == 3)
      ,
      test("not a line indented four, a lazy line, or the block's first line"):
        assertTrue(
          Tables
            .underline(block = 1, first = 1, reported = 3, source("Title", "    ---", "---")) == 3,
          Tables
            .underline(block = 1, first = 1, reported = 3, source("> Title", "===", "> ===")) == 3,
          Tables.underline(block = 1, first = 1, reported = 2, source("===", "---")) == 2
        )
      ,
      test("the reported end when no line before it is an underline"):
        assertTrue(
          Tables.underline(block = 1, first = 1, reported = 2, source("Title", "text")) == 2
        )
    ),
    suite("read underlined")(
      test("an underlined paragraph without a table is dropped, with no pipe or with one"):
        val title = Paragraph(1, List(text("Title", 1)))
        assertTrue(
          Tables.read(
            List(ParsedParagraph(title, lastLine = 2, underlined = true)),
            "Title\n---\n",
            unused
          ) == Right(TableBlocks(Nil, Nil)),
          Tables.read(
            List(ParsedParagraph(title, lastLine = 3, underlined = true)),
            "| Title |\n---\n\n",
            unused
          ) == Right(TableBlocks(Nil, Nil))
        )
      ,
      test("an underlined paragraph with a table is read as a paragraph, to its underline"):
        val content = "| A |\n|---|\n| x |\n---\n\n"
        val parse: String => Either[String, List[ParsedParagraph]] = {
          case "x A\n\nx x\n\n" =>
            Right(
              List(
                ParsedParagraph(
                  Paragraph(1, List(text("x A", 1))),
                  lastLine = 1,
                  underlined = false
                ),
                ParsedParagraph(
                  Paragraph(3, List(text("x x", 3))),
                  lastLine = 3,
                  underlined = false
                )
              )
            )
          case "---\n"  => Right(Nil)
          case document => Left(document)
        }
        assertTrue(
          Tables.read(
            List(ParsedParagraph(Paragraph(1, Nil), lastLine = 5, underlined = true)),
            content,
            parse
          ) == Right(
            TableBlocks(
              List(Paragraph(1, List(text("A", 1))), Paragraph(3, List(text("x", 3)))),
              Nil
            )
          )
        )
      ,
      test("after an underline row, the paragraphs that start on the next line join the table"):
        val content = "| A |\n|---|\n===\n| x |\n==\n| y |\n"
        val parsed = List(
          ParsedParagraph(Paragraph(1, Nil), lastLine = 4, underlined = true),
          ParsedParagraph(Paragraph(4, List(text("| x |", 4))), lastLine = 6, underlined = true),
          ParsedParagraph(Paragraph(6, List(text("| y |", 6))), lastLine = 6, underlined = false)
        )
        assertTrue(
          Tables.read(parsed, content, unused) ==
            Left("parsed again: x A\n\nx ===\n\nx x\n\nx ==\n\nx y\n\n")
        )
      ,
      test("no paragraph joins after a blank line, after a rule, or after a plain paragraph"):
        val parsed = (second: Int, underlined: Boolean) =>
          List(
            ParsedParagraph(Paragraph(1, Nil), lastLine = 3, underlined = underlined),
            ParsedParagraph(
              Paragraph(second, List(text("| x |", second))),
              lastLine = second,
              underlined = false
            )
          )
        assertTrue(
          Tables.read(parsed(5, true), "| A |\n|---|\n===\n\n| x |\n", unused) ==
            Left("parsed again: x A\n\nx ===\n\n"),
          Tables.read(parsed(4, true), "| A |\n|---|\n---\n| x |\n", unused) ==
            Left("parsed again: x A\n\n"),
          Tables.read(parsed(4, false), "| A |\n|---|\n| z |\n| x |\n", unused) ==
            Left("parsed again: x A\n\nx z\n\n")
        )
    ),
    suite("underline after definitions")(
      test("the search starts after the first line of text, past the definitions"):
        val lines = source("> [a]: /url", "> ===", "> | A |", "> |---|", "> | x |", "> ---")
        assertTrue(Tables.underline(block = 1, first = 2, reported = 6, lines) == 6)
      ,
      test("a definition, then an equals line, over a table: the line is the paragraph's text"):
        val content = "[a]: /url\n===\n| A |\n|---|\n| x |\n---\n"
        val parse: String => Either[String, List[ParsedParagraph]] = {
          case "[a]: /url\n" => Right(Nil)
          case document      => Left(document)
        }
        val heading = Paragraph(1, List(text("===", 2), break, text("| A |", 3)))
        assertTrue(
          Tables.read(
            List(ParsedParagraph(heading, lastLine = 6, underlined = true)),
            content,
            parse
          ) == Left("x ===\n\nx A\n\nx x\n\n")
        )
      ,
      test("a joining paragraph's underline is found after its own definitions"):
        val content = "| A |\n|---|\n===\n[b]: /u\n==\n| y |\n==\n| z |\n"
        val parse: String => Either[String, List[ParsedParagraph]] = {
          case "[b]: /u\n" => Right(Nil)
          case document    => Left(document)
        }
        val parsed = List(
          ParsedParagraph(Paragraph(1, Nil), lastLine = 4, underlined = true),
          ParsedParagraph(Paragraph(4, List(text("==", 5))), lastLine = 8, underlined = true),
          ParsedParagraph(Paragraph(8, List(text("| z |", 8))), lastLine = 8, underlined = false)
        )
        assertTrue(
          Tables.read(parsed, content, parse) ==
            Left("x A\n\nx ===\n\nx [b]: /u\n\nx ==\n\nx y\n\nx ==\n\nx z\n\n" + content),
          Tables.read(parsed, content, unused) == Left("parsed again: [b]: /u\n")
        )
    ),
    suite("firstLine")(
      test("the block's first line when the first piece is on it, or there's no lined piece"):
        assertTrue(
          Tables.firstLine(Paragraph(2, List(text("a", 2))), source(), unused) == Right(2),
          Tables.firstLine(Paragraph(2, Nil), source(), unused) == Right(2)
        )
      ,
      test("after the longest leading run that gives no paragraph, each line without its prefix"):
        val lines = source("> - [a]: /a", ">   [b]:", ">   /b", ">   text")
        val parse: String => Either[String, List[ParsedParagraph]] = {
          case "[a]: /a\n[b]:\n/b\n" | "[a]: /a\n[b]:\n" | "[a]: /a\n" => Right(Nil)
          case document                                                => Left(document)
        }
        assertTrue(Tables.firstLine(Paragraph(1, List(text("text", 4))), lines, parse) == Right(4))
      ,
      test("a leading line that gives a paragraph is the first line, with any after it"):
        val lines = source("[a]: /a", "![](i.png)", "text")
        val parse: String => Either[String, List[ParsedParagraph]] = {
          case "[a]: /a\n![](i.png)\n" =>
            Right(List(ParsedParagraph(Paragraph(2, Nil), lastLine = 2, underlined = false)))
          case "[a]: /a\n" => Right(Nil)
          case document    => Left(document)
        }
        assertTrue(Tables.firstLine(Paragraph(1, List(text("text", 3))), lines, parse) == Right(2))
      ,
      test("a first line that gives a paragraph on its own is the first line"):
        val parse: String => Either[String, List[ParsedParagraph]] = {
          case "![](i.png)\n" =>
            Right(List(ParsedParagraph(Paragraph(1, Nil), lastLine = 1, underlined = false)))
          case document => Left(document)
        }
        assertTrue(
          Tables.firstLine(
            Paragraph(1, List(text("text", 2))),
            source("![](i.png)", "text"),
            parse
          ) ==
            Right(1)
        )
    )
  )
