package io.github.tsnee.ears.markdown

import java.io.File
import java.nio.charset.StandardCharsets
import scala.scalanative.posix.dlfcn
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** The parts of cmark's C API the parser uses, all over opaque node pointers, so no C struct layout
  * is declared. Every function is in cmark 0.30 and 0.31. They're loaded when the parser runs, not
  * linked (see `CmarkLibrary`).
  */
private final case class Cmark(
    parseDocument: CFuncPtr3[CString, CSize, CInt, Ptr[Byte]],
    firstChild: CFuncPtr1[Ptr[Byte], Ptr[Byte]],
    next: CFuncPtr1[Ptr[Byte], Ptr[Byte]],
    typeString: CFuncPtr1[Ptr[Byte], CString],
    literal: CFuncPtr1[Ptr[Byte], CString],
    startLine: CFuncPtr1[Ptr[Byte], CInt],
    endLine: CFuncPtr1[Ptr[Byte], CInt],
    free: CFuncPtr1[Ptr[Byte], Unit]
)

/** Finds and loads an installed cmark: lists the directories `CmarkLibrary` names, tries its
  * candidates in order until one opens with every function and a supported release, and closes each
  * one it can't use. Nothing is kept between calls; once a library is open, `dlopen` returns the
  * same handle.
  */
private object CmarkLoader:

  /** The functions and the library they came from, for the log; or why there's none. */
  def load(): Either[MarkdownUnavailable, (Cmark, String)] =
    val platform =
      if Option(System.getProperty("os.name")).exists(_.startsWith("Mac")) then Platform.MacOs
      else Platform.Linux
    val loaderPath = Option(
      System.getenv(if platform == Platform.MacOs then "DYLD_LIBRARY_PATH" else "LD_LIBRARY_PATH")
    )
    val listing = CmarkLibrary
      .directories(platform, loaderPath, Option(System.getenv("HOMEBREW_PREFIX")))
      .map(directory =>
        directory -> Option(File(directory).list()).map(_.toList.flatMap(Option(_))).getOrElse(Nil)
      )
    CmarkLibrary
      .firstLoaded(CmarkLibrary.candidates(platform, listing), attempt)
      .left
      .map(CmarkLibrary.unavailable)

  private def attempt(path: String): Either[CmarkAttempt, (Cmark, String)] =
    val handle = Zone(present(dlfcn.dlopen(toCString(path), dlfcn.RTLD_NOW)))
    handle.toRight(CmarkAttempt.Unopened(path, lastError())).flatMap { opened =>
      val bound = bind(path, opened)
      if bound.isLeft then
        val _ = dlfcn.dlclose(opened)
      bound
    }

  /** `dlerror`'s description of the last failure. */
  private def lastError(): String =
    present(dlfcn.dlerror()).fold("no error reported")(fromCString(_))

  private def bind(path: String, handle: CVoidPtr): Either[CmarkAttempt, (Cmark, String)] =
    def symbol(name: CString): Either[CmarkAttempt, CVoidPtr] =
      present(dlfcn.dlsym(handle, name))
        .toRight(CmarkAttempt.MissingFunction(path, fromCString(name)))
    type Node = Ptr[Byte]
    for
      versionFunction <- symbol(c"cmark_version")
      version = CFuncPtr.fromPtr[CFuncPtr0[CInt]](versionFunction).apply()
      _ <- Either.cond(CmarkLibrary.supported(version), (), CmarkAttempt.TooOld(path, version))
      parseDocument <- symbol(c"cmark_parse_document")
      firstChild <- symbol(c"cmark_node_first_child")
      next <- symbol(c"cmark_node_next")
      typeString <- symbol(c"cmark_node_get_type_string")
      literal <- symbol(c"cmark_node_get_literal")
      startLine <- symbol(c"cmark_node_get_start_line")
      endLine <- symbol(c"cmark_node_get_end_line")
      free <- symbol(c"cmark_node_free")
    yield Cmark(
      CFuncPtr.fromPtr[CFuncPtr3[CString, CSize, CInt, Node]](parseDocument),
      CFuncPtr.fromPtr[CFuncPtr1[Node, Node]](firstChild),
      CFuncPtr.fromPtr[CFuncPtr1[Node, Node]](next),
      CFuncPtr.fromPtr[CFuncPtr1[Node, CString]](typeString),
      CFuncPtr.fromPtr[CFuncPtr1[Node, CString]](literal),
      CFuncPtr.fromPtr[CFuncPtr1[Node, CInt]](startLine),
      CFuncPtr.fromPtr[CFuncPtr1[Node, CInt]](endLine),
      CFuncPtr.fromPtr[CFuncPtr1[Node, Unit]](free)
    ) -> CmarkLibrary.loaded(path, version)

  /** A C pointer, or `None` for `NULL`, whichever way Scala Native represents it. */
  def present[A](pointer: Ptr[A]): Option[Ptr[A]] =
    Option(pointer).filter(_.toLong != 0L)

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

  /** The loaded cmark's release and path, for the log; or why there's none. */
  def name(): Either[MarkdownUnavailable, String] = CmarkLoader.load().map(_._2)

  /** The paragraphs, or what to install when no usable cmark is found. */
  def paragraphs(content: String): Either[String, List[Paragraph]] =
    CmarkLoader.load().left.map(_.message).map((cmark, _) => parsed(cmark, content))

  private def parsed(cmark: Cmark, content: String): List[Paragraph] =
    Zone.acquire { zone =>
      given Zone = zone
      // The content's UTF-8 bytes, once, with the terminating NUL C strings end with; the length
      // passed to cmark is explicit, so a NUL inside the content is read too.
      val bytes = content.getBytes(StandardCharsets.UTF_8)
      val buffer = alloc[Byte](bytes.length + 1)
      bytes.indices.foreach(i => buffer(i) = bytes(i))
      val document = cmark.parseDocument(buffer, bytes.length.toCSize, sourcePositions)
      // Everything is copied out of cmark's tree before it's freed.
      try paragraphNodes(cmark, document)
      finally cmark.free(document)
    }

  private def paragraphNodes(cmark: Cmark, document: Ptr[Byte]): List[Paragraph] =
    Walk
      .preorder(document, children(cmark, _))
      .filter(kind(cmark, _) == "paragraph")
      .map(paragraph =>
        CmarkLines.paragraph(
          Walk.preorder(paragraph, children(cmark, _)).flatMap(piece(cmark, _)),
          cmark.endLine(paragraph)
        )
      )

  /** A node's own piece; containers (emphasis, links, images) have none, only their children. */
  private def piece(cmark: Cmark, node: Ptr[Byte]): List[CmarkPiece] = kind(cmark, node) match
    case "text"                    => List(inlinePiece(cmark, node, false))
    case "code"                    => List(inlinePiece(cmark, node, true))
    case "softbreak" | "linebreak" => List(CmarkPiece.Break)
    case _                         => Nil

  private def inlinePiece(cmark: Cmark, node: Ptr[Byte], code: Boolean): CmarkPiece =
    CmarkPiece.Inline(literal(cmark, node), code, cmark.startLine(node), cmark.endLine(node))

  private def kind(cmark: Cmark, node: Ptr[Byte]): String = fromCString(cmark.typeString(node))

  private def literal(cmark: Cmark, node: Ptr[Byte]): String =
    CmarkLoader
      .present(cmark.literal(node))
      .fold("")(text => fromCString(text, StandardCharsets.UTF_8))

  private def children(cmark: Cmark, node: Ptr[Byte]): List[Ptr[Byte]] =
    List.unfold(CmarkLoader.present(cmark.firstChild(node)))(child =>
      child.map(found => (found, CmarkLoader.present(cmark.next(found))))
    )
