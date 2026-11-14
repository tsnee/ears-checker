package io.github.tsnee.ears.markdown

import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.tailrec

import zio.test.*

/** The decoder of the C entry point's records, on every platform: it inverts the records' encoding,
  * rejects malformed records, and decodes any depth of nesting without a deep stack.
  */
object CmarkTreeSuite extends ZIOSpecDefault:

  private val nowhere = CmarkPosition(0, 0, 0, 0)

  /** The records of a tree, as the entry point writes them: an enter record, then the children's
    * records, then an exit record.
    */
  private def encode(node: CmarkNode): String =
    val literal = node.literal.getBytes(UTF_8).length
    val position = node.position
    s"E ${node.kind.name} ${position.startLine} ${position.startColumn} " +
      s"${position.endLine} ${position.endColumn} $literal:${node.literal}\n" +
      node.children.map(encode).mkString + "X\n"

  private def decoded(records: String): Either[String, CmarkNode] =
    CmarkTree.decode(IArray.unsafeFromArray(records.getBytes(UTF_8)))

  /** How many levels deep the first children go, counted without recursion. */
  private def depth(node: CmarkNode): Int =
    @tailrec
    def loop(at: CmarkNode, levels: Int): Int = at.children match
      case first :: _ => loop(first, levels + 1)
      case Nil        => levels
    loop(node, 1)

  private val kinds: Gen[Any, CmarkType] =
    Gen.oneOf(Gen.elements(CmarkType.known*), Gen.const(CmarkType.Other("strikethrough")))

  private val literals: Gen[Any, String] =
    Gen.string(Gen.oneOf(Gen.alphaNumericChar, Gen.elements('\n', ':', ' ', 'X', 'é', '漢', '|')))

  private val positions: Gen[Any, CmarkPosition] =
    for
      startLine <- Gen.int(0, 9999)
      startColumn <- Gen.int(0, 999)
      endLine <- Gen.int(0, 9999)
      endColumn <- Gen.int(0, 999)
    yield CmarkPosition(startLine, startColumn, endLine, endColumn)

  private def nodes(levels: Int): Gen[Any, CmarkNode] =
    for
      kind <- kinds
      position <- positions
      literal <- literals
      children <-
        if levels <= 1 then Gen.const(Nil) else Gen.listOfBounded(0, 3)(nodes(levels - 1))
    yield CmarkNode(kind, position, literal, children)

  def spec = suite("CmarkTree")(
    test("decoding inverts the entry point's encoding"):
      check(nodes(4))(tree => assertTrue(decoded(encode(tree)) == Right(tree)))
    ,
    test("records the entry point wrote decode to its tree"):
      val records = "E document 1 1 1 22 0:\nE paragraph 1 1 1 22 0:\n" +
        "E text 1 1 1 10 10:The report\nX\nE code 1 12 1 16 5:shall\nX\n" +
        "E text 1 18 1 22 6: print\nX\nX\nX\n"
      assertTrue(
        decoded(records) == Right(
          CmarkNode(
            CmarkType.Document,
            CmarkPosition(1, 1, 1, 22),
            "",
            List(
              CmarkNode(
                CmarkType.Paragraph,
                CmarkPosition(1, 1, 1, 22),
                "",
                List(
                  CmarkNode(CmarkType.Text, CmarkPosition(1, 1, 1, 10), "The report", Nil),
                  CmarkNode(CmarkType.Code, CmarkPosition(1, 12, 1, 16), "shall", Nil),
                  CmarkNode(CmarkType.Text, CmarkPosition(1, 18, 1, 22), " print", Nil)
                )
              )
            )
          )
        )
      )
    ,
    test("each of cmark-gfm's type names the reader tells apart decodes to its type"):
      val names = List(
        "document" -> CmarkType.Document,
        "block_quote" -> CmarkType.BlockQuote,
        "list" -> CmarkType.ListBlock,
        "item" -> CmarkType.Item,
        "code_block" -> CmarkType.CodeBlock,
        "html_block" -> CmarkType.HtmlBlock,
        "paragraph" -> CmarkType.Paragraph,
        "heading" -> CmarkType.Heading,
        "thematic_break" -> CmarkType.ThematicBreak,
        "text" -> CmarkType.Text,
        "softbreak" -> CmarkType.SoftBreak,
        "linebreak" -> CmarkType.LineBreak,
        "code" -> CmarkType.Code,
        "html_inline" -> CmarkType.HtmlInline,
        "emph" -> CmarkType.Emph,
        "strong" -> CmarkType.Strong,
        "link" -> CmarkType.Link,
        "image" -> CmarkType.Image,
        "table" -> CmarkType.Table,
        "table_header" -> CmarkType.TableHeader,
        "table_row" -> CmarkType.TableRow,
        "table_cell" -> CmarkType.TableCell,
        "strikethrough" -> CmarkType.Other("strikethrough")
      )
      assertTrue(
        names.forall((name, kind) =>
          decoded(s"E $name 0 0 0 0 0:\nX\n").map(_.kind) == Right(kind)
        ),
        CmarkType.known.forall(kind => names.exists((_, named) => named == kind))
      )
    ,
    test("a literal holding a line break and an exit record's text is read by its length"):
      assertTrue(
        decoded("E text 1 1 2 2 4:a\nX\n\nX\n") ==
          Right(CmarkNode(CmarkType.Text, CmarkPosition(1, 1, 2, 2), "a\nX\n", Nil))
      )
    ,
    test("a literal's length counts bytes, not characters"):
      assertTrue(
        decoded("E text 1 1 1 2 5:é漢\nX\n").map(_.literal) == Right("é漢")
      )
    ,
    test("a tree 100,000 levels deep decodes"):
      val levels = 100000
      val records = "E block_quote 1 1 1 1 0:\n" * levels + "X\n" * levels
      assertTrue(decoded(records).map(depth) == Right(levels))
    ,
    suite("malformed records")(
      test("no records")(assertTrue(decoded("").isLeft)),
      test("a node left open")(assertTrue(decoded("E document 1 1 1 1 0:\n").isLeft)),
      test("an exit with nothing open")(assertTrue(decoded("X\n").isLeft)),
      test("two documents"):
        assertTrue(decoded("E document 1 1 1 1 0:\nX\nE document 1 1 1 1 0:\nX\n").isLeft)
      ,
      test("an unknown record")(assertTrue(decoded("Q\n").isLeft)),
      test("an exit without its line end")(assertTrue(decoded("E document 1 1 1 1 0:\nX").isLeft)),
      test("a position that isn't a number"):
        assertTrue(decoded("E text 1 1 x 3 0:\nX\n").isLeft)
      ,
      test("a number too long to be a position"):
        assertTrue(decoded("E text 1 1 12345678901 3 0:\nX\n").isLeft)
      ,
      test("a missing field")(assertTrue(decoded("E text 1 1 1 0:\nX\n").isLeft)),
      test("a literal longer than the records"):
        assertTrue(decoded("E text 1 1 1 1 99:ab\nX\n").isLeft)
      ,
      test("a literal not followed by its line end"):
        assertTrue(decoded("E text 1 1 1 1 1:abX\n").isLeft)
      ,
      test("a type name with no fields after it")(assertTrue(decoded("E text").isLeft)),
      test("an enter record with nothing after its E"):
        assertTrue(
          decoded("E") == Left("the enter record at byte 0 is cut short"),
          decoded("Etext 1 1 1 1 0:\nX\n") == Left("the enter record at byte 0 is cut short")
        )
    ),
    test("the position with no line is the one cmark gives a node it didn't place"):
      assertTrue(
        decoded("E paragraph 0 0 0 0 0:\nX\n").map(_.position) == Right(nowhere)
      )
  )
