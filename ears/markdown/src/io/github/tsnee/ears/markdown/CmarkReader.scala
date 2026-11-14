package io.github.tsnee.ears.markdown

import java.nio.charset.StandardCharsets.UTF_8

/** What the reader takes from a document: each paragraph and table cell, in document order, and the
  * warnings.
  */
private[markdown] final case class ReadBlocks(
    paragraphs: List[Paragraph],
    warnings: List[MarkdownWarning]
) derives CanEqual

/** Reading cmark-gfm's tree (`CmarkTree`), the same on every platform: every paragraph, list items
  * and block quotes included, and every table cell, each with its lines; and a warning for each
  * table body row with more cells than its header.
  *
  * cmark-gfm's positions are corrected where they're wrong. Inline lines are cmark's
  * (`CmarkLines`). A table that interrupts a paragraph leaves that paragraph with no position, its
  * inline lines counted from 0, and gives the header row the paragraph's line; but the table's
  * start line, its body rows' lines, and its end line are right. So the delimiter row is the line
  * before the first body row (the table's last line when it has none), the header row the line
  * before that, and the interrupted paragraph ends on the line before the header and starts on the
  * table's first line. A cell is one line: its row's.
  */
private[markdown] object CmarkReader:

  def read(document: CmarkNode, source: String): ReadBlocks =
    // The source's lines, for counting table rows' cells: split once, only if there's a table.
    lazy val lines = Lines.of(source)
    val read = Walk.preorder[(CmarkNode, Option[CmarkNode])]((document, None), blocks).map {
      case (node, next) =>
        node.kind match
          case CmarkType.Paragraph => ReadBlocks(List(paragraph(node, next)), Nil)
          case CmarkType.Table     => table(node, lines)
          case _                   => ReadBlocks(Nil, Nil)
    }
    ReadBlocks(read.flatMap(_.paragraphs), read.flatMap(_.warnings))

  /** A block's children, each with the sibling after it; none for a paragraph or a table, which are
    * read whole.
    */
  private def blocks(block: (CmarkNode, Option[CmarkNode])): List[(CmarkNode, Option[CmarkNode])] =
    val (node, _) = block
    node.kind match
      case CmarkType.Paragraph | CmarkType.Table => Nil
      case _ => node.children.zip(node.children.drop(1).map(Some(_)) :+ None)

  /** A paragraph, placed from its end line, or from the table after it when that table interrupted
    * it; its block starts no later than its first placed line, from the link reference definitions
    * that open it.
    */
  private def paragraph(node: CmarkNode, next: Option[CmarkNode]): Paragraph =
    val (start, end) = next match
      case Some(table) if table.kind == CmarkType.Table && node.position.startLine == 0 =>
        (table.position.startLine, headerLine(table) - 1)
      case _ => (node.position.startLine, node.position.endLine)
    val placed =
      CmarkLines.paragraph(pieces(node, inline => (inline.startLine, inline.endLine)), end)
    placed.copy(line = start.min(placed.line))

  /** The line of a table's header row: two before its first body row, or one before its last line,
    * the delimiter row, when it has no body rows.
    */
  private def headerLine(table: CmarkNode): Int =
    rows(table)
      .map(_.position.startLine - 1)
      .headOption
      .getOrElse(table.position.endLine) - 1

  private def rows(table: CmarkNode): List[CmarkNode] =
    table.children.filter(_.kind == CmarkType.TableRow)

  private def table(node: CmarkNode, lines: Vector[String]): ReadBlocks =
    val header = headerLine(node)
    val headerCells = node.children.filter(_.kind == CmarkType.TableHeader).flatMap(_.children)
    val body = rows(node)
    ReadBlocks(
      headerCells.map(cell(_, header)) ++
        body.flatMap(row => row.children.map(cell(_, row.position.startLine))),
      body.flatMap(extraCells(_, headerCells.size, lines))
    )

  /** A cell, all on its row's line. */
  private def cell(node: CmarkNode, line: Int): Paragraph =
    CmarkLines.paragraph(pieces(node, _ => (line, line)), line)

  /** A body row's warning when it has more cells than the header and any extra one has text: the
    * cells counted from the row's source, since cmark-gfm drops the extra ones, and split as GitHub
    * splits them. The row is its line up to its end column (bytes of UTF-8) without the container
    * markers before it. cmark-gfm gives every body row the table's start column, whatever the row's
    * own prefix, so the markers are stripped here instead: a body row is never a list item's first
    * line, and a row can't start with `>` (the line would start a block quote), so they're only
    * indentation and block quote markers.
    */
  private def extraCells(
      row: CmarkNode,
      headerCells: Int,
      lines: Vector[String]
  ): Option[MarkdownWarning] =
    val position = row.position
    val line = lines.lift(position.startLine - 1).getOrElse("").getBytes(UTF_8)
    val text = String(line.take(position.endColumn), UTF_8)
    val cells = Rows.cells(text.dropWhile(char => char == ' ' || char == '\t' || char == '>'))
    Option.when(cells.drop(headerCells).exists(_.nonEmpty))(
      MarkdownWarning.ExtraCells(line = position.startLine, cells = cells.size, headerCells)
    )

  /** The text, code, and breaks under a node, each inline with the lines `lines` gives it;
    * containers (emphasis, links, images) have no piece of their own, only their children, and
    * inline HTML none.
    */
  private def pieces(node: CmarkNode, lines: CmarkPosition => (Int, Int)): List[CmarkPiece] =
    Walk.preorder[CmarkNode](node, _.children).flatMap { inline =>
      val (start, end) = lines(inline.position)
      inline.kind match
        case CmarkType.Text => List(CmarkPiece.Inline(inline.literal, false, start, end))
        case CmarkType.Code => List(CmarkPiece.Inline(inline.literal, true, start, end))
        case CmarkType.SoftBreak | CmarkType.LineBreak => List(CmarkPiece.Break)
        case _                                         => Nil
    }
