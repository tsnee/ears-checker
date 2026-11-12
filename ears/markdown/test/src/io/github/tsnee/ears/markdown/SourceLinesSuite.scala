package io.github.tsnee.ears.markdown

import zio.test.*

/** `SourceLines`: each piece's line, found in the paragraph's source, as Node's parser
  * (commonmark.js, which gives inline nodes no positions) needs it. Every platform runs it.
  */
object SourceLinesSuite extends ZIOSpecDefault:

  private def text(value: String) = SourcePiece.Inline(value, false)
  private def code(value: String) = SourcePiece.Inline(value, true)

  private def lines(paragraph: Paragraph): List[Option[Int]] = paragraph.pieces.map(_.line)

  /** Lines of words, each piece some of a line's words, with a break between lines. */
  private val genLines: Gen[Any, List[List[List[String]]]] =
    Gen.listOfBounded(1, 6)(
      Gen.listOfBounded(1, 4)(Gen.listOfBounded(1, 3)(Gen.alphaNumericStringBounded(1, 6)))
    )

  def spec = suite("SourceLines")(
    test("one line: every piece on the start line, and the paragraph too"):
      val found = SourceLines.paragraph(
        "The `x` shall list.",
        7,
        List(text("The "), code("x"), text(" shall list."))
      )
      assertTrue(
        found.line == 7,
        found.pieces.map(_.text) == List("The ", "x", " shall list."),
        found.pieces.map(_.code) == List(false, true, false),
        lines(found) == List(Some(7), Some(7), Some(7))
      )
    ,
    test("a break is a space with no line, and the text after it is on the next line"):
      val found = SourceLines.paragraph(
        "Intro.\nThe scheduler shall list.",
        1,
        List(text("Intro."), SourcePiece.Break, text("The scheduler shall list."))
      )
      assertTrue(
        found.pieces(1) == Piece(" ", false, None),
        lines(found) == List(Some(1), None, Some(2))
      )
    ,
    test(
      "a code span across lines: its space matches the line ending, and what follows is on the next line"
    ):
      val found = SourceLines.paragraph(
        "The `scheduler\nservice` shall list events.",
        1,
        List(text("The "), code("scheduler service"), text(" shall list events."))
      )
      assertTrue(lines(found) == List(Some(1), Some(1), Some(2)))
    ,
    test("a link whose title is on the next line: the hidden line ending is passed"):
      val found = SourceLines.paragraph(
        "[link](u\n\"t\") x.\nThe scheduler shall list.",
        1,
        List(text("link"), text(" x."), SourcePiece.Break, text("The scheduler shall list."))
      )
      assertTrue(lines(found) == List(Some(1), Some(2), None, Some(3)))
    ,
    test("inline HTML across lines, dropped, is passed too"):
      val found = SourceLines.paragraph(
        "Intro <span\nclass=\"x\">a</span> b.",
        4,
        List(text("Intro "), text("a"), text(" b."))
      )
      assertTrue(lines(found) == List(Some(4), Some(5), Some(5)))
    ,
    test("in a block quote, a code span across lines matches past the next line's marker"):
      val found = SourceLines.paragraph(
        "> Intro `a\n> b` c.\n> The scheduler shall list.",
        2,
        List(
          text("Intro "),
          code("a b"),
          text(" c."),
          SourcePiece.Break,
          text("The scheduler shall list.")
        )
      )
      assertTrue(lines(found) == List(Some(2), Some(2), Some(3), None, Some(4)))
    ,
    test("text an entity changed takes the line it's on, and the text after a break the next"):
      val found = SourceLines.paragraph(
        "Some \\*escaped\\* text &amp;amp; more.\nThe scheduler shall list.",
        1,
        List(
          text("Some *escaped* text &amp; more."),
          SourcePiece.Break,
          text("The scheduler shall list.")
        )
      )
      assertTrue(lines(found) == List(Some(1), None, Some(2)))
    ,
    test("text found nowhere takes the line where the search stands"):
      val found = SourceLines.paragraph(
        "Alpha\nbeta gamma.",
        1,
        List(text("Alpha"), SourcePiece.Break, text("beta"), text("\n"), text(" gamma."))
      )
      assertTrue(lines(found) == List(Some(1), None, Some(2), Some(2), Some(2)))
    ,
    test(
      "joined: text right after its previous sibling is one piece; code, breaks, gaps stay apart"
    ):
      // Keys are (parent, position among its siblings); 0 is the paragraph, 1 a link in it.
      // Position 7, missing, is dropped inline HTML, as the parser leaves it.
      val found = SourceLines.joined(
        List(
          text("Print ") -> (0, 0),
          text("\\") -> (0, 1),
          text("`") -> (0, 2),
          text(" now ") -> (0, 3),
          code("x") -> (0, 4),
          text("link") -> (1, 0),
          text(" after") -> (0, 6),
          text(" html") -> (0, 8),
          SourcePiece.Break -> (0, 9),
          text("ok") -> (0, 10),
          text(".") -> (0, 11)
        ),
        (k, j) => k._1 == j._1 && j._2 == k._2 + 1
      )
      assertTrue(
        found == List(
          text("Print \\` now "),
          code("x"),
          text("link"),
          text(" after"),
          text(" html"),
          SourcePiece.Break,
          text("ok.")
        )
      )
    ,
    test(
      "an escaped first word after a hidden line ending matches the punctuation it decodes to"
    ):
      // The inline HTML hides a line ending, so only matching `\*b\*` as `*b*` finds the text on
      // line 2; unmatched, it would stay where the search stands, on line 1.
      val found = SourceLines.paragraph(
        "Intro <span\nclass=x>\\*b\\* c.",
        1,
        List(text("Intro "), text("*b* c."))
      )
      assertTrue(lines(found) == List(Some(1), Some(2)))
    ,
    test("text that runs past the end of the source isn't found"):
      val found =
        SourceLines.paragraph("Intro.\nab", 1, List(text("Intro."), SourcePiece.Break, text("abc")))
      assertTrue(lines(found) == List(Some(1), None, Some(2)))
    ,
    test("a break with no line ending after it moves nothing"):
      val found = SourceLines.paragraph("a", 3, List(text("a"), SourcePiece.Break, text("b")))
      assertTrue(lines(found) == List(Some(3), None, Some(3)))
    ,
    test("rows of unmatched text still advance a line at each break"):
      val found = SourceLines.paragraph(
        "a &amp; b\nc &amp; d\ne &amp; f\ng",
        1,
        List(
          text("a & b"),
          SourcePiece.Break,
          text("c & d"),
          SourcePiece.Break,
          text("e & f"),
          SourcePiece.Break,
          text("g")
        )
      )
      assertTrue(lines(found) == List(Some(1), None, Some(2), None, Some(3), None, Some(4)))
    ,
    test("speed guard: a long paragraph with an entity on every line stays linear"):
      // A speed guard for the bounded search window: text an entity changed is never found whole,
      // so each search reads its whole window. Bounded to the next line, the paragraph takes well
      // under a second; searching to the end of the source, it takes many seconds (14 s against
      // 0.6 s, measured 2026-11-11) and the lines still come out right. There's no timeout, which
      // would make the test flaky: a slow run here is the failure to look for.
      val rows = 3000
      val found = SourceLines.paragraph(
        List.fill(rows)("Tom &amp; Jerry shall run.").mkString("\n"),
        1,
        List.fill(rows)(text("Tom & Jerry shall run.")).flatMap(List(SourcePiece.Break, _)).drop(1)
      )
      assertTrue(lines(found).flatten == (1 to rows).toList)
    ,
    test("text that matches only on a later line, where its twin is written plainly, stays put"):
      val found = SourceLines.paragraph(
        "Tom &amp; Jerry run.\nThe x runs.\nTom & Jerry run.\n",
        1,
        List(
          text("Tom & Jerry run."),
          SourcePiece.Break,
          text("The x runs."),
          SourcePiece.Break,
          text("Tom & Jerry run.")
        )
      )
      assertTrue(lines(found) == List(Some(1), None, Some(2), None, Some(3)))
    ,
    test("a break never moves the search past the paragraph's last line"):
      val found =
        SourceLines.paragraph("a\n", 4, List(text("a"), SourcePiece.Break, text("b")))
      assertTrue(lines(found) == List(Some(4), None, Some(4)))
    ,
    test("matched: in text an escaped backslash is one backslash; in code backslashes are literal"):
      assertTrue(
        SourceLines.matched("C:\\\\dir", 0, "C:\\dir", false) == Some(7),
        SourceLines.matched("\\*a", 0, "*a", false) == Some(3),
        SourceLines.matched("a\\\\b", 0, "a\\\\b", true) == Some(4),
        SourceLines.matched("\\*a", 0, "*a", true) == None
      )
    ,
    test("CRLF and CR each end one line"):
      val crlf = SourceLines.paragraph(
        "a\r\nb\r\nc",
        1,
        List(text("a"), SourcePiece.Break, text("b"), SourcePiece.Break, text("c"))
      )
      val cr = SourceLines.paragraph(
        "a\rb\rc",
        1,
        List(text("a"), SourcePiece.Break, text("b"), SourcePiece.Break, text("c"))
      )
      assertTrue(
        lines(crlf) == List(Some(1), None, Some(2), None, Some(3)),
        lines(cr) == List(Some(1), None, Some(2), None, Some(3))
      )
    ,
    test("text that also appears later is found at its first place after the last match"):
      val found =
        SourceLines.paragraph("a b.\na b.", 1, List(text("a b."), SourcePiece.Break, text("a b.")))
      assertTrue(lines(found) == List(Some(1), None, Some(2)))
    ,
    test(
      "words split into pieces on each line: each piece is on its line, and lines never decrease"
    ):
      check(genLines) { paragraph =>
        val source = paragraph.map(_.map(_.mkString(" ")).mkString(" ")).mkString("\n")
        val pieces = paragraph.zipWithIndex.flatMap { (line, i) =>
          (if i == 0 then Nil else List(SourcePiece.Break)) ++
            line.zipWithIndex.map((words, j) =>
              text((if j == 0 then "" else " ") + words.mkString(" "))
            )
        }
        val expected = paragraph.zipWithIndex.flatMap { (line, i) =>
          (if i == 0 then Nil else List(None)) ++ line.map(_ => Some(i + 1))
        }
        val found = lines(SourceLines.paragraph(source, 1, pieces))
        assertTrue(found == expected, found.flatten == found.flatten.sorted)
      }
  )
