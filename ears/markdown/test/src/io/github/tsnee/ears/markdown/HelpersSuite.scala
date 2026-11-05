package io.github.tsnee.ears.markdown

import zio.test.*

/** The pure helpers behind the parsers, on every platform: `CmarkLines` (cmark's lines, which only
  * the native parser uses), `LineEndings` (mdast's, which only the Node parser uses), and `Walk`.
  */
object HelpersSuite extends ZIOSpecDefault:

  private def textAt(text: String, start: Int, end: Int) =
    CmarkPiece.Inline(text, false, start, end)

  private def lines(paragraph: Paragraph): List[Option[Int]] = paragraph.pieces.map(_.line)

  def spec = suite("Markdown helpers")(
    suite("CmarkLines")(
      test("after a hard break cmark doesn't advance; the stretch after it is on the next line"):
        val found = CmarkLines.paragraph(
          List(textAt("Intro.", 1, 1), CmarkPiece.Break, textAt("The scheduler", 1, 1)),
          2
        )
        assertTrue(found.line == 1, lines(found) == List(Some(1), None, Some(2)))
      ,
      test("after a reference definition everything is a line early; the end line puts it right"):
        val found = CmarkLines.paragraph(List(textAt("The scheduler", 1, 1)), 2)
        assertTrue(found.line == 2, lines(found) == List(Some(2)))
      ,
      test("a code span across lines keeps its stretch's lines relative to each other"):
        val found = CmarkLines.paragraph(
          List(
            textAt("The ", 1, 1),
            CmarkPiece.Inline("a b", true, 1, 2),
            textAt(" c.", 2, 2),
            CmarkPiece.Break,
            textAt("The scheduler", 3, 3)
          ),
          3
        )
        assertTrue(
          found.line == 1,
          lines(found) == List(Some(1), Some(1), Some(2), None, Some(3)),
          found.pieces(1).code
        )
      ,
      test("a paragraph with no pieces is on its end line"):
        assertTrue(CmarkLines.paragraph(Nil, 4) == Paragraph(4, Nil))
      ,
      test("a break before any text starts a stretch of its own"):
        val found = CmarkLines.paragraph(List(CmarkPiece.Break, textAt("x", 1, 1)), 2)
        assertTrue(found.line == 1, lines(found) == List(None, Some(2)))
    ),
    suite("LineEndings")(
      test("every line ending in the source is real"):
        assertTrue(
          LineEndings.lines("a\nb\r\nc", "a\nb\r\nc") == List("a", "b", "c"),
          LineEndings.lines("one", "one") == List("one")
        )
      ,
      test("a line ending decoded from &#10; stays in its line"):
        assertTrue(LineEndings.lines("Intro.&#10;The", "Intro.\nThe") == List("Intro.\nThe"))
      ,
      test("decoded and real line endings together"):
        assertTrue(LineEndings.lines("x&#10;y\nz", "x\ny\nz") == List("x\ny", "z"))
      ,
      test("hexadecimal, carriage-return, and named references"):
        assertTrue(
          LineEndings.lines("a&#x0D;b", "a\rb") == List("a\rb"),
          LineEndings.lines("a&#13;b\nc", "a\rb\nc") == List("a\rb", "c"),
          LineEndings.lines("a&NewLine;b", "a\nb") == List("a\nb")
        )
      ,
      test("a source and a value that don't line up: every line ending in the value counts"):
        assertTrue(LineEndings.lines("a\nb\nc", "a\nb") == List("a", "b"))
    ),
    suite("Walk")(
      test("every node, root first, in document order"):
        val tree = Map(1 -> List(2, 5), 2 -> List(3, 4), 5 -> List(6))
        assertTrue(Walk.preorder[Int](1, tree.getOrElse(_, Nil)) == List(1, 2, 3, 4, 5, 6))
      ,
      test("a chain 100,000 deep, without running out of stack"):
        val depth = 100000
        val found = Walk.preorder[Int](0, n => if n < depth then List(n + 1) else Nil)
        assertTrue(found.size == depth + 1, found.lastOption.contains(depth))
    )
  )
