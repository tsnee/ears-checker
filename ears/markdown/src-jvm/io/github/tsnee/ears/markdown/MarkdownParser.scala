package io.github.tsnee.ears.markdown

import scala.jdk.CollectionConverters.*

import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** The JVM's Markdown parser, commonmark-java: every paragraph, list items included, as pieces of
  * text and inline code with their lines. commonmark-java gives each text and code node its own
  * source span, so every piece but a line break has a line.
  */
private[markdown] object MarkdownParser:

  private val parser =
    Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()

  /** The parser's name, for the log; it's part of the program, so always there. */
  def name(): Either[MarkdownUnavailable, String] = Right("commonmark-java")

  /** The paragraphs; always found, since this parser is part of the program. */
  def paragraphs(content: String): Either[String, List[Paragraph]] = Right(parsed(content))

  private def parsed(content: String): List[Paragraph] =
    Walk.preorder(parser.parse(content), children).collect {
      case paragraph: org.commonmark.node.Paragraph =>
        Paragraph(
          line(paragraph).getOrElse(1),
          Walk.preorder[Node](paragraph, children).flatMap(piece)
        )
    }

  private def children(node: Node): List[Node] =
    List.unfold(Option(node.getFirstChild))(child => child.map(c => (c, Option(c.getNext))))

  /** The 1-based line a node starts on; commonmark-java counts from 0. */
  private def line(node: Node): Option[Int] =
    node.getSourceSpans.asScala.headOption.map(_.getLineIndex + 1)

  /** A node's own piece; containers (emphasis, links, images) have none, only their children. */
  private def piece(node: Node): List[Piece] = node match
    case text: Text       => List(Piece(Option(text.getLiteral).getOrElse(""), false, line(text)))
    case code: Code       => List(Piece(Option(code.getLiteral).getOrElse(""), true, line(code)))
    case _: SoftLineBreak => List(Piece(" ", false, None))
    case _: HardLineBreak => List(Piece(" ", false, None))
    case _                => Nil
