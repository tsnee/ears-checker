package io.github.tsnee.ears.markdown

import scala.annotation.tailrec

/** The paragraphs to find sentences in (each table cell is one, and so are the lines before a
  * table), and the warnings.
  */
private[markdown] final case class TableBlocks(
    paragraphs: List[Paragraph],
    warnings: List[MarkdownWarning]
) derives CanEqual

/** Text from a table paragraph to read again: its `lines`, and the 1-based line of the document it
  * starts on. A cell is one line.
  */
private[markdown] final case class Segment(lines: List[String], line: Int) derives CanEqual

/** A paragraph that holds a table: its segments, to read as inline text (the lines before the
  * table, if any, then each nonempty cell of the header and body rows, in order); its rows'
  * warnings; and the lines `after` the table's end, if it ends before the paragraph does, to read
  * as the Markdown they are.
  */
private[markdown] final case class TableLayout(
    segments: List[Segment],
    warnings: List[MarkdownWarning],
    after: Option[Segment]
) derives CanEqual

/** GitHub's tables, which none of the parsers recognizes, read in GitHub's own order: rows and
  * cells from the raw text first (`Rows`), each cell's inline formatting after. In a paragraph, a
  * line followed by a delimiter row with as many cells starts a table, and later lines are body
  * rows until one at which GitHub ends the table (`Rows.endsTable`); the lines before it stay a
  * paragraph, and the lines from the end on are read again as a document of their own. The cells
  * and the lines before a table are read again by the platform's parser, all in one document per
  * source document.
  */
private[markdown] object Tables:

  /** What goes before each segment in the document read again: it makes the segment a plain
    * paragraph, never a list, heading, quote, fence, HTML block, or link definition.
    */
  private val prefix = "x "

  /** What goes before each later line of a segment: an indent that can't start any block, so the
    * line continues the paragraph, as it did in the source.
    */
  private val indent = "    "

  /** The paragraphs, each table paragraph replaced by its segments read as inline text and the
    * Markdown after its table, and the warnings. `content` is the document; `parse`, the platform's
    * parser, reads the segments, once, and only when there are some. A document with no pipe has no
    * table, and isn't looked at.
    */
  def read(
      parsed: List[ParsedParagraph],
      content: String,
      parse: String => Either[String, List[ParsedParagraph]]
  ): Either[String, TableBlocks] =
    if !content.contains('|') then Right(TableBlocks(parsed.map(_.paragraph), Nil))
    else
      val lines = Lines.of(content)
      val source = (line: Int) => lines.lift(line - 1).getOrElse("")
      for
        layouts <- traverse(parsed)(found => table(found, source, parse).map(found -> _))
        segments = layouts.flatMap((_, laid) => laid.toList.flatMap(_.segments))
        cells <-
          if segments.isEmpty then Right(Nil)
          else
            val original = if segments.exists(_.lines.exists(_.contains('['))) then content else ""
            parse(document(segments, original)).map(found =>
              placed(segments, found.map(_.paragraph))
            )
        afters <- traverse(layouts.flatMap((_, laid) => laid.flatMap(_.after).toList))(
          readAfter(_, content, parse)
        )
      yield assemble(layouts, cells, afters)

  /** The blocks in order: each paragraph without a table as it is; for one with a table, its
    * segments' paragraphs from `cells`, then its table's warnings, then what was read after the
    * table, from `afters`.
    */
  private def assemble(
      layouts: List[(ParsedParagraph, Option[TableLayout])],
      cells: List[Paragraph],
      afters: List[TableBlocks]
  ): TableBlocks =
    val (blocks, _, _) =
      layouts.foldLeft((Vector.empty[TableBlocks], cells, afters)) {
        case ((done, cellsLeft, aftersLeft), (found, None)) =>
          (done :+ TableBlocks(List(found.paragraph), Nil), cellsLeft, aftersLeft)
        case ((done, cellsLeft, aftersLeft), (_, Some(laid))) =>
          val (mine, laterCells) = cellsLeft.splitAt(laid.segments.size)
          val (after, laterAfters) = aftersLeft.splitAt(laid.after.size)
          (done :+ TableBlocks(mine, laid.warnings) :++ after, laterCells, laterAfters)
      }
    TableBlocks(blocks.toList.flatMap(_.paragraphs), blocks.toList.flatMap(_.warnings))

  /** The paragraph's table, if it holds one. Only a later line with a pipe and a dash can be a
    * delimiter row, so most paragraphs stop at that check, before their first line is looked for.
    */
  private def table(
      found: ParsedParagraph,
      source: Int => String,
      parse: String => Either[String, List[ParsedParagraph]]
  ): Either[String, Option[TableLayout]] =
    val block = found.paragraph.line
    if !mayHoldDelimiter(block, found.lastLine, source) then Right(None)
    else
      firstLine(found.paragraph, source, parse).map(first =>
        layout(block, first, found.lastLine, source)
      )

  /** Whether a line after `first`, up to `last`, has a pipe and a dash. */
  private def mayHoldDelimiter(first: Int, last: Int, source: Int => String): Boolean =
    (first + 1 to last).exists { line =>
      val text = source(line)
      text.contains('|') && text.contains('-')
    }

  /** The paragraph's first line of text, from `source`: the first line of its block (its `line`),
    * after the definitions that open the block, if any. When the paragraph's first lined piece is
    * on its block's first line (or it has none), that's the line; otherwise it's the line after the
    * longest run of the block's leading lines before that piece that, read by `parse` on their own
    * (without their container prefixes), give no paragraph. A line before the first piece that
    * gives one is text with no piece, such as an image alone.
    */
  def firstLine(
      paragraph: Paragraph,
      source: Int => String,
      parse: String => Either[String, List[ParsedParagraph]]
  ): Either[String, Int] =
    val block = paragraph.line
    val piece = paragraph.pieces.flatMap(_.line).headOption.getOrElse(block)
    @tailrec
    def longest(last: Int): Either[String, Int] =
      if last < block then Right(block)
      else
        val run = (block to last).map(line => Rows.stripped(source(line), first = line == block))
        parse(run.mkString("", "\n", "\n")) match
          case Left(error)                   => Left(error)
          case Right(found) if found.isEmpty => Right(last + 1)
          case Right(_)                      => longest(last - 1)
    longest(piece - 1)

  /** The paragraph's table, if it holds one, from its raw lines `first` to `lastLine`, each from
    * `source`; its containers are those its block's first line, `block`, opens. A delimiter row
    * must match those containers and be indented less than four columns past their content. A body
    * row with a cell with text beyond the header's count gets a warning, and its extra cells are
    * left out.
    */
  def layout(block: Int, first: Int, lastLine: Int, source: Int => String): Option[TableLayout] =
    if !mayHoldDelimiter(first, lastLine, source) then None
    else
      val (containers, opening) = Rows.opened(source(block))
      val rows = (first to lastLine).toList.map { line =>
        line -> (if line == block then opening else Rows.matched(source(line), containers))
      }
      val start = rows
        .zip(rows.drop(1))
        .indexWhere { case ((_, header), (_, delimiter)) =>
          !delimiter.isLazy && delimiter.indent < 4 && Rows.isDelimiter(delimiter.text) &&
          Rows.cells(header.text).size == Rows.cells(delimiter.text).size
        }
      rows.splitAt(start) match
        case (before, (headerLine, header) :: _ :: later) if start >= 0 =>
          val columns = Rows.cells(header.text).size
          val (body, rest) = later.span((_, row) => !Rows.endsTable(row))
          val split =
            ((headerLine, header) :: body).map((line, row) => line -> Rows.cells(row.text))
          Some(
            TableLayout(
              segments =
                before.headOption.map((line, _) => Segment(before.map(_._2.text), line)).toList ++
                  split.flatMap((line, cells) =>
                    cells.take(columns).filter(_.nonEmpty).map(cell => Segment(List(cell), line))
                  ),
              warnings = split.drop(1).collect {
                case (line, cells) if cells.drop(columns).exists(_.nonEmpty) =>
                  MarkdownWarning.ExtraCells(line = line, cells = cells.size, headerCells = columns)
              },
              after = rest.headOption.map((line, _) => Segment(afterLines(rest, source), line))
            )
          )
        case _ => None

  /** The lines after a table's end, to read as a document: each without the table's container
    * prefix until the first lazy one, and from there as written, since GitHub has closed the
    * containers it doesn't match.
    */
  private def afterLines(rest: List[(Int, RowLine)], source: Int => String): List[String] =
    val (inside, outside) = rest.span((_, row) => !row.isLazy)
    inside.map(_._2.inner) ++ outside.map((line, _) => source(line))

  /** The lines after a table read as a document of their own (followed, when they hold a `[`, by a
    * blank line and the whole `content`, for its link reference definitions), with tables of their
    * own, and placed on their lines in the source.
    */
  private def readAfter(
      after: Segment,
      content: String,
      parse: String => Either[String, List[ParsedParagraph]]
  ): Either[String, TableBlocks] =
    val own = after.lines.mkString("", "\n", "\n")
    val document = if own.contains('[') then own + "\n" + content else own
    parse(document)
      .flatMap(found => read(found.filter(_.lastLine <= after.lines.size), document, parse))
      .map(blocks => shifted(blocks, after.line - 1))

  /** The blocks with every line moved down by `by`. */
  private def shifted(blocks: TableBlocks, by: Int): TableBlocks =
    TableBlocks(
      blocks.paragraphs.map(paragraph =>
        Paragraph(
          paragraph.line + by,
          paragraph.pieces.map(piece => piece.copy(line = piece.line.map(_ + by)))
        )
      ),
      blocks.warnings.map { case MarkdownWarning.ExtraCells(line, cells, headerCells) =>
        MarkdownWarning.ExtraCells(line = line + by, cells = cells, headerCells = headerCells)
      }
    )

  /** The results of `f` on each of `items`, or its first failure. */
  private def traverse[A, B](items: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    items
      .foldLeft[Either[String, Vector[B]]](Right(Vector.empty))((done, item) =>
        done.flatMap(found => f(item).map(found :+ _))
      )
      .map(_.toList)

  /** The document read again: each segment as a paragraph of its own (its first line after
    * `prefix`, its later lines after `indent`), separated by blank lines, then a blank line and
    * `original`: the whole source when a segment holds a `[`, which gives the segments its link
    * reference definitions, and can't change how they're read, coming after; otherwise empty.
    */
  def document(segments: List[Segment], original: String): String =
    segments
      .map(segment =>
        segment.lines.zipWithIndex
          .map((text, index) => (if index == 0 then prefix else indent) + text)
          .mkString("\n")
      )
      .mkString("", "\n\n", "\n\n") + original

  /** The first paragraphs of the document read again, one per segment, each on its segment's line,
    * with its pieces' lines moved from the document read again to the source, and the prefix
    * removed.
    */
  def placed(segments: List[Segment], parsed: List[Paragraph]): List[Paragraph] =
    val starts = segments.scanLeft(1)((start, segment) => start + segment.lines.size + 1)
    segments.zip(starts).zip(parsed).map { case ((segment, start), paragraph) =>
      Paragraph(
        segment.line,
        withoutPrefix(paragraph.pieces, prefix.length)
          .map(piece => piece.copy(line = piece.line.map(_ - start + segment.line)))
      )
    }

  /** The pieces without their first `count` characters, which are text. */
  @tailrec
  private def withoutPrefix(pieces: List[Piece], count: Int): List[Piece] = pieces match
    case first :: rest if count > 0 =>
      if first.text.length <= count then withoutPrefix(rest, count - first.text.length)
      else first.copy(text = first.text.drop(count)) :: rest
    case _ => pieces
