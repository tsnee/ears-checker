package io.github.tsnee.ears.markdown

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** The JVM's Markdown parser, commonmark-java: every paragraph, list items included, and every
  * setext heading, underlined, as pieces of text and inline code with their lines. commonmark-java
  * gives each text and code node its own source span, so every piece but a line break has a line.
  */
private[markdown] object MarkdownParser:

  private val parser =
    Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()

  /** The parser's name, for the log; it's part of the program, so always there. */
  def name(): Either[MarkdownUnavailable, String] = Right("commonmark-java")

  /** The parser, reading a document into its paragraphs; always found, since it's part of the
    * program.
    */
  def loaded(): Either[String, String => List[ParsedParagraph]] = Right(parsed)

  private def parsed(content: String): List[ParsedParagraph] =
    Walk.preorder(parser.parse(content), children).flatMap {
      case paragraph: org.commonmark.node.Paragraph => List(block(paragraph, underlined = false))
      // A block's source spans are one per line, so a heading with more than one is a setext
      // heading: an ATX heading is one line.
      case heading: Heading if heading.getSourceSpans.size > 1 =>
        List(block(heading, underlined = true))
      case _ => Nil
    }

  /** A paragraph, or a setext heading, as a parsed paragraph. */
  private def block(node: Node, underlined: Boolean): ParsedParagraph =
    val first = line(node).getOrElse(1)
    ParsedParagraph(
      Paragraph(blockStart(node, first), Walk.preorder[Node](node, children).flatMap(piece)),
      lastLine = node.getSourceSpans.asScala.lastOption.fold(first)(_.getLineIndex + 1),
      underlined = underlined
    )

  /** The first line of the paragraph's block, which starts `first`: commonmark-java gives the link
    * reference definitions that open a block as nodes of their own, right before the paragraph,
    * each ending on the line before the next; commonmark.js and cmark count them in the paragraph.
    */
  @tailrec
  private def blockStart(node: Node, first: Int): Int =
    Option(node.getPrevious) match
      case Some(definition: LinkReferenceDefinition)
          if definition.getSourceSpans.asScala.lastOption.exists(_.getLineIndex + 2 == first) =>
        blockStart(definition, line(definition).getOrElse(first))
      case _ => first

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
