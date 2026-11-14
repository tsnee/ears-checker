package io.github.tsnee.ears.markdown

import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.tailrec

/** cmark-gfm's type of a node, by the name its entry point writes (`ears/markdown/c`); a type the
  * reader doesn't tell apart is `Other`, with its name.
  */
private[markdown] enum CmarkType derives CanEqual:
  case Document, BlockQuote, ListBlock, Item, CodeBlock, HtmlBlock, Paragraph, Heading,
    ThematicBreak, Text, SoftBreak, LineBreak, Code, HtmlInline, Emph, Strong, Link, Image, Table,
    TableHeader, TableRow, TableCell
  case Other(other: String)

private[markdown] object CmarkType:

  private val byName: Map[String, CmarkType] = Map(
    "document" -> Document,
    "block_quote" -> BlockQuote,
    "list" -> ListBlock,
    "item" -> Item,
    "code_block" -> CodeBlock,
    "html_block" -> HtmlBlock,
    "paragraph" -> Paragraph,
    "heading" -> Heading,
    "thematic_break" -> ThematicBreak,
    "text" -> Text,
    "softbreak" -> SoftBreak,
    "linebreak" -> LineBreak,
    "code" -> Code,
    "html_inline" -> HtmlInline,
    "emph" -> Emph,
    "strong" -> Strong,
    "link" -> Link,
    "image" -> Image,
    "table" -> Table,
    "table_header" -> TableHeader,
    "table_row" -> TableRow,
    "table_cell" -> TableCell
  )

  /** The type cmark-gfm calls `name`. */
  def of(name: String): CmarkType = byName.getOrElse(name, Other(name))

  /** Every type but `Other`: those the reader tells apart. */
  def known: List[CmarkType] = byName.values.toList

  extension (kind: CmarkType)
    /** The name cmark-gfm gives the type. */
    def name: String = kind match
      case Other(other) => other
      case known        => byName.collectFirst { case (name, `known`) => name }.getOrElse("")

/** Where cmark-gfm says a node is: 1-based lines and byte columns, all 0 for a node it gives no
  * position.
  */
private[markdown] final case class CmarkPosition(
    startLine: Int,
    startColumn: Int,
    endLine: Int,
    endColumn: Int
) derives CanEqual

/** A node of cmark-gfm's tree, with its literal text (empty for most nodes) and its children. */
private[markdown] final case class CmarkNode(
    kind: CmarkType,
    position: CmarkPosition,
    literal: String,
    children: List[CmarkNode]
) derives CanEqual

/** Decoding the records the C entry point writes (`ears/markdown/c/ears_cmark.c` says their form)
  * into cmark-gfm's tree. The same records come from every platform, so this is the whole of the
  * parser's output the reader sees. Decoding keeps its own stack, so a tree of any depth decodes.
  */
private[markdown] object CmarkTree:

  /** A node whose enter record was read and whose exit record wasn't yet, with its children so far,
    * last first.
    */
  private final case class Open(
      kind: CmarkType,
      position: CmarkPosition,
      literal: String,
      children: List[CmarkNode]
  )

  /** The document the records describe, or what's wrong with them. */
  def decode(records: IArray[Byte]): Either[String, CmarkNode] =
    @tailrec
    def loop(at: Int, open: List[Open], done: List[CmarkNode]): Either[String, CmarkNode] =
      if at == records.length then
        (open, done) match
          case (Nil, List(document)) => Right(document)
          case (Nil, _) => Left(s"the records hold ${done.length} top nodes, not one document")
          case (_, _)   => Left(s"the records end with ${open.length} nodes not exited")
      else
        records(at).toChar match
          case 'X' =>
            (byteAt(at + 1), open) match
              case (Some('\n'), node :: rest) =>
                val closed =
                  CmarkNode(node.kind, node.position, node.literal, node.children.reverse)
                rest match
                  case parent :: outer =>
                    loop(at + 2, parent.copy(children = closed :: parent.children) :: outer, done)
                  case Nil => loop(at + 2, Nil, closed :: done)
              case (Some('\n'), Nil) => Left(s"an exit record at byte $at exits no node")
              case _                 => Left(s"the exit record at byte $at has no line end")
          case 'E' =>
            enter(at + 1) match
              case Right((node, next)) => loop(next, node :: open, done)
              case Left(message)       => Left(message)
          case other => Left(s"byte $at starts no record: '$other'")

    def byteAt(at: Int): Option[Char] =
      if at < records.length then Some(records(at).toChar) else None

    // The UTF-8 text of the bytes from `from` up to `until`.
    def text(from: Int, until: Int): String =
      String(IArray.genericWrapArray(records.slice(from, until)).toArray, UTF_8)

    // The text from `at` up to `end` (exclusive), and the offset after `end`.
    def field(at: Int, end: Char): Either[String, (String, Int)] =
      (at until records.length).find(records(_).toChar == end) match
        case Some(stop) => Right((text(at, stop), stop + 1))
        case None       => Left(s"the field at byte $at has no '$end' after it")

    def number(at: Int, end: Char): Either[String, (Int, Int)] =
      field(at, end).flatMap { (digits, next) =>
        if digits.nonEmpty && digits.length <= 9 && digits.forall(_.isDigit) then
          Right((digits.toInt, next))
        else Left(s"the field at byte $at isn't a number: '$digits'")
      }

    // An enter record's fields, from just after its `E`: the node, and the offset after the record.
    def enter(at: Int): Either[String, (Open, Int)] =
      for
        _ <- Either.cond(
          byteAt(at).contains(' '),
          (),
          s"the enter record at byte ${at - 1} is cut short"
        )
        (name, afterName) <- field(at + 1, ' ')
        (startLine, afterStartLine) <- number(afterName, ' ')
        (startColumn, afterStartColumn) <- number(afterStartLine, ' ')
        (endLine, afterEndLine) <- number(afterStartColumn, ' ')
        (endColumn, afterEndColumn) <- number(afterEndLine, ' ')
        (length, literalStart) <- number(afterEndColumn, ':')
        literalEnd = literalStart + length
        _ <- Either.cond(
          byteAt(literalEnd).contains('\n'),
          (),
          s"the literal at byte $literalStart isn't $length bytes followed by a line end"
        )
      yield (
        Open(
          CmarkType.of(name),
          CmarkPosition(startLine, startColumn, endLine, endColumn),
          text(literalStart, literalEnd),
          Nil
        ),
        literalEnd + 1
      )

    loop(0, Nil, Nil)
