package io.github.tsnee.ears.markdown

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** mdast's parser, from the npm package `mdast-util-from-markdown`. */
@js.native
@JSImport("mdast-util-from-markdown", "fromMarkdown")
private object fromMarkdown extends js.Object:
  def apply(markdown: String): js.Dynamic = js.native

/** Node's Markdown parser, mdast: every paragraph, list items included, as the same pieces
  * commonmark-java gives on the JVM. mdast differs in four ways, each undone here:
  *   - it keeps a paragraph's line endings inside `text` values, so a text run is split into one
  *     piece per line, at the line endings that are in the source (`LineEndings`: a decoded `&#10;`
  *     isn't one);
  *   - it keeps a line ending inside inline code, which CommonMark reads as a space;
  *   - it gives an image's alt text, for `image` and `imageReference` alike, as an attribute;
  *   - it gives inline HTML tags as nodes of their own, which are dropped.
  */
private[markdown] object MarkdownParser:

  private val lineEnding = "\r\n|\r|\n"

  def paragraphs(content: String): List[Paragraph] =
    Walk
      .preorder(fromMarkdown(content), children)
      .filter(kind(_) == "paragraph")
      .map(paragraph =>
        Paragraph(
          startLine(paragraph),
          Walk.preorder(paragraph, children).flatMap(piece(content, _))
        )
      )

  /** A node's own pieces; containers (emphasis, links) have none, only their children. */
  private def piece(content: String, node: js.Dynamic): List[Piece] = kind(node) match
    case "text" =>
      split(LineEndings.lines(source(content, node), text(node, "value")), startLine(node))
    case "inlineCode" =>
      List(Piece(text(node, "value").replaceAll(lineEnding, " "), true, Some(startLine(node))))
    case "image" | "imageReference" =>
      split(text(node, "alt").split(lineEnding, -1).toList, startLine(node))
    case "break" => List(Piece(" ", false, None))
    case _       => Nil

  /** One piece per line, on successive lines, with a line break between each. */
  private def split(lines: List[String], first: Int): List[Piece] =
    lines.zipWithIndex.flatMap((line, i) =>
      Option.when(i > 0)(Piece(" ", false, None)).toList :+ Piece(line, false, Some(first + i))
    )

  /** The node's text in the document, from its offsets. */
  private def source(content: String, node: js.Dynamic): String =
    (number(node.position.start.offset), number(node.position.end.offset)) match
      case (Some(from), Some(to)) if 0 <= from && from <= to && to <= content.length =>
        content.substring(from, to)
      case _ => ""

  private def kind(node: js.Dynamic): String = text(node, "type")

  /** A string field, or empty when it's missing (an image without alt text, say). */
  private def text(node: js.Dynamic, field: String): String =
    (node.selectDynamic(field): Matchable) match
      case value: String => value
      case _             => ""

  private def number(value: js.Dynamic): Option[Int] = (value: Matchable) match
    case found: Int => Some(found)
    case _          => None

  private def startLine(node: js.Dynamic): Int = number(node.position.start.line).getOrElse(1)

  private def children(node: js.Dynamic): List[js.Dynamic] =
    (node.children: Matchable) match
      case array: js.Array[?] =>
        List.range(0, array.length).map(i => node.children.selectDynamic(i.toString))
      case _ => Nil
