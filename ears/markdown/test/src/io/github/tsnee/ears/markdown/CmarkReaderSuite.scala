package io.github.tsnee.ears.markdown

import zio.test.*

/** Reading cmark-gfm's tree into paragraphs, table cells, and warnings, on trees shaped as
  * cmark-gfm 0.29.0.gfm.13 gives them (`cmark-gfm --sourcepos -e table -t xml`): its positions
  * around a table that interrupts a paragraph corrected, and a row's extra cells counted from its
  * source.
  */
object CmarkReaderSuite extends ZIOSpecDefault:

  private def leaf(kind: CmarkType, position: (Int, Int, Int, Int), literal: String): CmarkNode =
    val (startLine, startColumn, endLine, endColumn) = position
    CmarkNode(kind, CmarkPosition(startLine, startColumn, endLine, endColumn), literal, Nil)

  private def node(kind: CmarkType, position: (Int, Int, Int, Int))(
      children: CmarkNode*
  ): CmarkNode =
    leaf(kind, position, "").copy(children = children.toList)

  private def text(position: (Int, Int, Int, Int), literal: String): CmarkNode =
    leaf(CmarkType.Text, position, literal)

  private val nowhere = (0, 0, 0, 0)

  private def cell(literal: String): CmarkNode =
    node(CmarkType.TableCell, nowhere)(text(nowhere, literal))

  private def piece(text: String, line: Int): Piece = Piece(text, code = false, line = Some(line))

  private val break = Piece(" ", code = false, line = None)

  /** "Reminders are sent daily.", "The scheduler shall send reminders.", then a table, ending on
    * `tableEnd`, whose header is "The calendar shall sync.", with `bodyRows`: the tree cmark-gfm
    * gives it, with no position for the paragraph or its first line, its other lines counted from
    * 0, and the header placed on the paragraph's line.
    */
  private def interrupted(bodyRows: List[CmarkNode], tableEnd: Int): List[CmarkNode] = List(
    node(CmarkType.Paragraph, nowhere)(
      text(nowhere, "Reminders are sent daily."),
      leaf(CmarkType.SoftBreak, nowhere, ""),
      text((1, 0, 1, 34), "The scheduler shall send reminders.")
    ),
    node(CmarkType.Table, (1, 1, tableEnd, 33))(
      (node(CmarkType.TableHeader, (1, 1, 1, 99))(cell("The calendar shall sync.")) ::
        bodyRows)*
    )
  )

  private def document(blocks: CmarkNode*): CmarkNode =
    node(CmarkType.Document, (1, 1, 1, 1))(blocks*)

  def spec = suite("CmarkReader")(
    test("a paragraph cmark-gfm placed keeps its lines"):
      val read = CmarkReader.read(
        document(
          node(CmarkType.Paragraph, (2, 1, 3, 24))(
            text((2, 1, 2, 35), "The scheduler shall send reminders."),
            leaf(CmarkType.SoftBreak, (2, 36, 2, 36), ""),
            leaf(CmarkType.Code, (3, 2, 3, 6), "shall"),
            text((3, 8, 3, 24), " sync, calendar.")
          )
        ),
        "\nThe scheduler shall send reminders.\n`shall` sync, calendar.\n"
      )
      assertTrue(
        read == ReadBlocks(
          List(
            Paragraph(
              2,
              List(
                piece("The scheduler shall send reminders.", 2),
                break,
                Piece("shall", code = true, line = Some(3)),
                piece(" sync, calendar.", 3)
              )
            )
          ),
          Nil
        )
      )
    ,
    test("a paragraph a table interrupts ends on the line before the header row"):
      val row = node(CmarkType.TableRow, (5, 1, 5, 5))(
        node(CmarkType.TableCell, (5, 2, 5, 4))(text((5, 3, 5, 3), "A"))
      )
      val read = CmarkReader.read(
        document(interrupted(List(row), tableEnd = 5)*),
        "Reminders are sent daily.\nThe scheduler shall send reminders.\n" +
          "| The calendar shall sync. |\n|---|\n| A |\n"
      )
      assertTrue(
        read.paragraphs == List(
          Paragraph(
            1,
            List(
              piece("Reminders are sent daily.", 1),
              break,
              piece("The scheduler shall send reminders.", 2)
            )
          ),
          Paragraph(3, List(piece("The calendar shall sync.", 3))),
          Paragraph(5, List(piece("A", 5)))
        )
      )
    ,
    test("a table with no body rows has its header on the line before its last"):
      val read = CmarkReader.read(
        document(interrupted(Nil, tableEnd = 4)*),
        "Reminders are sent daily.\nThe scheduler shall send reminders.\n" +
          "| The calendar shall sync. |\n|---|\n"
      )
      assertTrue(
        read.paragraphs.map(_.line) == List(1, 3),
        read.paragraphs.flatMap(_.pieces).flatMap(_.line) == List(1, 2, 3)
      )
    ,
    test("a table that interrupts nothing keeps the paragraph before it as placed"):
      val read = CmarkReader.read(
        document(
          node(CmarkType.Paragraph, (1, 1, 1, 26))(
            text((1, 1, 1, 26), "Reminders are sent daily.")
          ),
          node(CmarkType.Table, (3, 1, 5, 5))(
            node(CmarkType.TableHeader, (3, 1, 3, 10))(cell("Rule")),
            node(CmarkType.TableRow, (5, 1, 5, 5))(cell("A"))
          )
        ),
        "Reminders are sent daily.\n\n| Rule |\n|---|\n| A |\n"
      )
      assertTrue(read.paragraphs.map(_.line) == List(1, 3, 5))
    ,
    test("a body row's cells are on the row's line, whatever cmark-gfm gives their text"):
      val read = CmarkReader.read(
        document(
          node(CmarkType.Table, (1, 1, 3, 30))(
            node(CmarkType.TableHeader, (1, 1, 1, 10))(cell("Rule")),
            node(CmarkType.TableRow, (3, 1, 3, 30))(
              node(CmarkType.TableCell, (3, 2, 3, 6))(
                text((1, 1, 1, 1), "The "),
                node(CmarkType.Emph, nowhere)(text(nowhere, "scheduler")),
                leaf(CmarkType.HtmlInline, nowhere, "<br>"),
                leaf(CmarkType.Code, (2, 1, 2, 1), "shall")
              )
            )
          )
        ),
        "| Rule |\n|---|\n| The *scheduler*<br>`shall` |\n"
      )
      assertTrue(
        read.paragraphs.drop(1) == List(
          Paragraph(
            3,
            List(
              piece("The ", 3),
              piece("scheduler", 3),
              Piece("shall", code = true, line = Some(3))
            )
          )
        )
      )
    ,
    suite("extra cells")(
      test("a body row with more cells than its header, counted from its source"):
        val read = CmarkReader.read(
          document(
            node(CmarkType.Table, (1, 1, 3, 13))(
              node(CmarkType.TableHeader, (1, 1, 1, 9))(cell("ID"), cell("Rule")),
              node(CmarkType.TableRow, (3, 1, 3, 13))(cell("A"), cell("x"))
            )
          ),
          "| ID | Rule |\n|---|---|\n| A | x | y |\n"
        )
        assertTrue(read.warnings == List(MarkdownWarning.ExtraCells(3, 3, 2)))
      ,
      test("the row's columns are bytes of UTF-8, after a block quote's marker"):
        val read = CmarkReader.read(
          document(
            node(CmarkType.BlockQuote, (1, 1, 3, 18))(
              node(CmarkType.Table, (1, 3, 3, 18))(
                node(CmarkType.TableHeader, (1, 3, 1, 11))(cell("ID"), cell("Rule")),
                node(CmarkType.TableRow, (3, 3, 3, 18))(cell("é"), cell("漢"))
              )
            )
          ),
          "> | ID | Rule |\n> |---|---|\n> | é | 漢 | y |\n"
        )
        assertTrue(read.warnings == List(MarkdownWarning.ExtraCells(3, 3, 2)))
      ,
      test("a row is read from its own line, not from the table's start column"):
        // cmark-gfm gives each body row the table's start column (3, after "> "), whatever the
        // row's own prefix: here row 3's is narrower (">") and row 4's wider ("  > ").
        val read = CmarkReader.read(
          document(
            node(CmarkType.BlockQuote, (1, 1, 4, 13))(
              node(CmarkType.Table, (1, 3, 4, 13))(
                node(CmarkType.TableHeader, (1, 3, 1, 15))(cell("ID"), cell("Rule")),
                node(CmarkType.TableRow, (3, 3, 3, 10))(cell("A"), cell("x")),
                node(CmarkType.TableRow, (4, 3, 4, 13))(cell("B"), cell("y"))
              )
            )
          ),
          "> | ID | Rule |\n> |---|---|\n>A | x | z\n  > | B | y |\n"
        )
        assertTrue(read.warnings == List(MarkdownWarning.ExtraCells(3, 3, 2)))
      ,
      test("an escaped pipe doesn't split a cell, and extra cells that are empty are no warning"):
        val read = CmarkReader.read(
          document(
            node(CmarkType.Table, (1, 1, 4, 11))(
              node(CmarkType.TableHeader, (1, 1, 1, 9))(cell("ID"), cell("Rule")),
              node(CmarkType.TableRow, (3, 1, 3, 14))(cell("A"), cell("x | y")),
              node(CmarkType.TableRow, (4, 1, 4, 11))(cell("B"), cell("z"))
            )
          ),
          "| ID | Rule |\n|---|---|\n| A | x \\| y |\n| B | z | |\n"
        )
        assertTrue(read.warnings == Nil)
    )
  )
