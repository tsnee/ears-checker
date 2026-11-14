package io.github.tsnee.ears.markdown

import zio.test.*

/** Each platform's call of the C entry point (`CmarkGfm`): cmark-gfm's release, and the records of
  * a short document, which decode to the tree `cmark-gfm --sourcepos -e table -t xml` gives for it.
  */
object CmarkGfmSuite extends ZIOSpecDefault:

  private def leaf(kind: CmarkType, position: (Int, Int, Int, Int), literal: String): CmarkNode =
    val (startLine, startColumn, endLine, endColumn) = position
    CmarkNode(kind, CmarkPosition(startLine, startColumn, endLine, endColumn), literal, Nil)

  private def node(kind: CmarkType, position: (Int, Int, Int, Int))(
      children: CmarkNode*
  ): CmarkNode =
    leaf(kind, position, "").copy(children = children.toList)

  private def text(position: (Int, Int, Int, Int), literal: String): CmarkNode =
    leaf(CmarkType.Text, position, literal)

  private val document =
    "The scheduler SHALL list events.\n\n| ID | Requirement |\n|---|---|\n" +
      "| A | Events SHALL be `sorted`. |\n"

  private val tree = node(CmarkType.Document, (1, 1, 5, 33))(
    node(CmarkType.Paragraph, (1, 1, 1, 32))(
      text((1, 1, 1, 32), "The scheduler SHALL list events.")
    ),
    node(CmarkType.Table, (3, 1, 5, 33))(
      node(CmarkType.TableHeader, (3, 1, 3, 20))(
        node(CmarkType.TableCell, (3, 2, 3, 5))(text((3, 3, 3, 4), "ID")),
        node(CmarkType.TableCell, (3, 7, 3, 19))(text((3, 8, 3, 18), "Requirement"))
      ),
      node(CmarkType.TableRow, (5, 1, 5, 33))(
        node(CmarkType.TableCell, (5, 2, 5, 4))(text((5, 3, 5, 3), "A")),
        node(CmarkType.TableCell, (5, 6, 5, 32))(
          text((5, 7, 5, 22), "Events SHALL be "),
          leaf(CmarkType.Code, (5, 24, 5, 29), "sorted"),
          text((5, 31, 5, 31), ".")
        )
      )
    )
  )

  def spec = suite("CmarkGfm")(
    test("the release is cmark-gfm's 0.29.0.gfm.13"):
      assertTrue(CmarkGfm.version() == Right("0.29.0.gfm.13"))
    ,
    test("a short document's records decode to cmark-gfm's tree"):
      assertTrue(CmarkGfm.records(document).flatMap(CmarkTree.decode) == Right(tree))
    ,
    test("text that isn't ASCII keeps its bytes"):
      val records = CmarkGfm.records("Le système SHALL trier les événements.\n")
      assertTrue(
        records.flatMap(CmarkTree.decode).map(_.children.flatMap(_.children)) ==
          Right(List(text((1, 1, 1, 41), "Le système SHALL trier les événements.")))
      )
    ,
    test("positions and lengths of five digits come through"):
      val literal = "a" * 12345
      val records = CmarkGfm.records("\n" * 10000 + literal + "\n")
      assertTrue(
        records.flatMap(CmarkTree.decode).map(_.children.flatMap(_.children)) ==
          Right(List(text((10001, 1, 10001, 12345), literal)))
      )
    ,
    test("an empty document is a document with no children"):
      assertTrue(CmarkGfm.records("").flatMap(CmarkTree.decode).map(_.children) == Right(Nil))
    ,
    test("a NULL pointer means cmark-gfm returned nothing"):
      assertTrue(
        CmarkCall.allocated(0) ==
          Left("cmark-gfm returned nothing: it ran out of memory, or its parser couldn't start"),
        CmarkCall.allocated(1024) == Right(1024)
      )
    ,
    test("records with no NUL after them are a failure, not all of memory"):
      assertTrue(
        CmarkCall.terminated(-1) == Left("cmark-gfm's records have no NUL ending them"),
        CmarkCall.terminated(4096) == Right(4096)
      )
  )
