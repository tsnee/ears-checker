package io.github.tsnee.ears.markdown

import java.nio.charset.StandardCharsets
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** The parts of cmark's C API the parser uses, all over opaque node pointers, so no C struct layout
  * is declared. Every function is in cmark 0.30 and 0.31.
  */
@extern
private object cmark:
  def cmark_parse_document(buffer: CString, length: CSize, options: CInt): Ptr[Byte] = extern
  def cmark_node_first_child(node: Ptr[Byte]): Ptr[Byte] = extern
  def cmark_node_next(node: Ptr[Byte]): Ptr[Byte] = extern
  def cmark_node_get_type_string(node: Ptr[Byte]): CString = extern
  def cmark_node_get_literal(node: Ptr[Byte]): CString = extern
  def cmark_node_get_start_line(node: Ptr[Byte]): CInt = extern
  def cmark_node_get_end_line(node: Ptr[Byte]): CInt = extern
  def cmark_node_free(node: Ptr[Byte]): Unit = extern

/** Scala Native's Markdown parser, cmark, CommonMark's reference C implementation: every paragraph,
  * list items included, as the same pieces commonmark-java gives on the JVM. cmark splits text at
  * soft breaks and gives an image's alt text as text, as commonmark-java does; its inline lines are
  * wrong in places, so `CmarkLines` corrects them from what it gets right. Node types are read by
  * name, since `cmark_node_type`'s values are implicit and not promised across versions.
  */
private[markdown] object MarkdownParser:

  /** `CMARK_OPT_SOURCEPOS` (`1 << 1` in cmark 0.30 and 0.31). Without it, inline nodes' start lines
    * are wrong after a code span that crosses a line: a C probe of cmark 0.31.2 gave the text after
    * ``The `scheduler⏎service` …`` line 1 under `CMARK_OPT_DEFAULT`, and line 2 with this.
    */
  private val sourcePositions = 1 << 1

  def paragraphs(content: String): List[Paragraph] =
    Zone.acquire { zone =>
      given Zone = zone
      // The content's UTF-8 bytes, once, with the terminating NUL C strings end with; the length
      // passed to cmark is explicit, so a NUL inside the content is read too.
      val bytes = content.getBytes(StandardCharsets.UTF_8)
      val buffer = alloc[Byte](bytes.length + 1)
      bytes.indices.foreach(i => buffer(i) = bytes(i))
      val document = cmark.cmark_parse_document(buffer, bytes.length.toCSize, sourcePositions)
      // Everything is copied out of cmark's tree before it's freed.
      try paragraphNodes(document)
      finally cmark.cmark_node_free(document)
    }

  private def paragraphNodes(document: Ptr[Byte]): List[Paragraph] =
    Walk
      .preorder(document, children)
      .filter(kind(_) == "paragraph")
      .map(paragraph =>
        CmarkLines.paragraph(
          Walk.preorder(paragraph, children).flatMap(piece),
          cmark.cmark_node_get_end_line(paragraph)
        )
      )

  /** A node's own piece; containers (emphasis, links, images) have none, only their children. */
  private def piece(node: Ptr[Byte]): List[CmarkPiece] = kind(node) match
    case "text"                    => List(inlinePiece(node, false))
    case "code"                    => List(inlinePiece(node, true))
    case "softbreak" | "linebreak" => List(CmarkPiece.Break)
    case _                         => Nil

  private def inlinePiece(node: Ptr[Byte], code: Boolean): CmarkPiece =
    CmarkPiece.Inline(
      literal(node),
      code,
      cmark.cmark_node_get_start_line(node),
      cmark.cmark_node_get_end_line(node)
    )

  private def kind(node: Ptr[Byte]): String = fromCString(cmark.cmark_node_get_type_string(node))

  private def literal(node: Ptr[Byte]): String =
    present(cmark.cmark_node_get_literal(node)).fold("")(text =>
      fromCString(text, StandardCharsets.UTF_8)
    )

  private def children(node: Ptr[Byte]): List[Ptr[Byte]] =
    List.unfold(present(cmark.cmark_node_first_child(node)))(child =>
      child.map(found => (found, present(cmark.cmark_node_next(found))))
    )

  /** A C pointer, or `None` for `NULL`, whichever way Scala Native represents it. */
  private def present[A](pointer: Ptr[A]): Option[Ptr[A]] =
    Option(pointer).filter(_.toLong != 0L)
