package io.github.tsnee.ears.markdown

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** commonmark.js's parser, from the npm package `commonmark`. */
@js.native
@JSImport("commonmark", "Parser")
private class CommonmarkParser() extends js.Object:
  def parse(markdown: String): js.Dynamic = js.native

/** Node's Markdown parser, commonmark.js: every paragraph, list items and block quotes included, as
  * the same pieces commonmark-java gives on the JVM. commonmark.js gives source positions only for
  * blocks, so each piece's line is found in the paragraph's source (`SourceLines`). Its tree is
  * otherwise CommonMark's, like the JVM's: text, code with a space for each line ending, soft and
  * hard breaks as nodes, an image's alt text as its children, and inline HTML (dropped) as nodes.
  * It splits text at each escape, so text right after its previous sibling is joined
  * (`SourceLines.joined`).
  */
private[markdown] object MarkdownParser:

  /** The parser's name, for the log; it's part of the program, so always there. */
  def name(): Either[MarkdownUnavailable, String] = Right("commonmark.js")

  /** The parser, reading a document into its paragraphs; always found, since it's part of the
    * program.
    */
  def loaded(): Either[String, String => List[ParsedParagraph]] = Right(parsed)

  private def parsed(content: String): List[ParsedParagraph] =
    val starts = Lines.starts(content)
    Walk
      .preorder(CommonmarkParser().parse(content), children)
      .filter(kind(_) == "paragraph")
      .map { paragraph =>
        val first = line(paragraph, 0)
        val last = line(paragraph, 1).max(first)
        ParsedParagraph(
          SourceLines.paragraph(
            source(content, starts, first, last),
            first,
            SourceLines.joined(
              Walk.preorder(paragraph, children).flatMap(node => piece(node).map(_ -> node)),
              (earlier, later) => later.prev eq earlier
            )
          ),
          last
        )
      }

  /** A node's own piece; containers (emphasis, links, images) have none, only their children. */
  private def piece(node: js.Dynamic): List[SourcePiece] = kind(node) match
    case "text"                    => List(SourcePiece.Inline(literal(node), false))
    case "code"                    => List(SourcePiece.Inline(literal(node), true))
    case "softbreak" | "linebreak" => List(SourcePiece.Break)
    case _                         => Nil

  /** Lines `first` to `last` (1-based) of the document, whole. */
  private def source(content: String, starts: Vector[Int], first: Int, last: Int): String =
    val from = starts.lift(first - 1).getOrElse(content.length)
    val to = starts.lift(last).getOrElse(content.length)
    content.substring(from.min(to), to)

  /** The start (`end` 0) or end (`end` 1) line of a block, from its `sourcepos`. */
  private def line(node: js.Dynamic, end: Int): Int =
    (node.sourcepos.selectDynamic(end.toString).selectDynamic("0"): Matchable) match
      case found: Int => found
      case _          => 1

  private def kind(node: js.Dynamic): String = string(node.selectDynamic("type"))

  /** A text or code node's literal, or empty when it has none. */
  private def literal(node: js.Dynamic): String = string(node.literal)

  private def string(value: js.Dynamic): String = (value: Matchable) match
    case found: String => found
    case _             => ""

  private def children(node: js.Dynamic): List[js.Dynamic] =
    List.unfold(Option(node.firstChild))(child => child.map(c => (c, Option(c.next))))
