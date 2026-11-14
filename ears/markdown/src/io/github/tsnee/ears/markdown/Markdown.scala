package io.github.tsnee.ears.markdown

/** A run of a paragraph's text: inline code or not, and the 1-based line it starts on. A line break
  * inside a paragraph is a piece of one space with no line of its own.
  */
final case class Piece(text: String, code: Boolean, line: Option[Int]) derives CanEqual

/** A paragraph, list items and table cells included, and the 1-based line its block starts on: the
  * line of the first link reference definition that opens the block, if one does.
  */
final case class Paragraph(line: Int, pieces: List[Piece]) derives CanEqual

/** A requirement sentence and the 1-based line it starts on. */
final case class Sentence(line: Int, text: String) derives CanEqual

/** Something the reader saw in a document that a user may want to know, though it isn't a finding:
  * plain data, worded by its user.
  */
enum MarkdownWarning derives CanEqual:
  /** A table's body row on `line` has `cells` cells, more than its header's `headerCells`; GitHub
    * doesn't show the extra ones, and the reader doesn't read them.
    */
  case ExtraCells(line: Int, cells: Int, headerCells: Int)

/** What the reader found in a document: its requirement sentences, and its warnings. */
final case class MarkdownText(sentences: List[Sentence], warnings: List[MarkdownWarning])
    derives CanEqual

/** Requirement sentences in Markdown, the same on every platform: cmark-gfm, GitHub's parser, with
  * its table extension, parses the document (`CmarkGfm`, the one call that differs by platform),
  * and shared code decodes its tree (`CmarkTree`), reads the paragraphs and table cells in it
  * (`CmarkReader`), and finds the sentences (`Extraction`).
  */
object Markdown:

  /** The parser that reads Markdown, with its release, for the log; or why it can't run. */
  def parser(): Either[String, String] = parserName

  /** The parser's name, found once: on the JVM and Node, asking cmark-gfm its release starts a
    * WebAssembly instance, and the answer never changes.
    */
  private lazy val parserName: Either[String, String] =
    CmarkGfm.version().map(release => s"cmark-gfm $release")

  /** Each sentence of each paragraph and table cell, list items included, that contains the modal
    * keyword outside inline code, with the line it starts on. Headings, code blocks, and HTML
    * blocks have no paragraphs, so nothing in them is read. Fails only when the parser itself does
    * (out of memory, say), saying why.
    */
  def sentences(content: String): Either[String, List[Sentence]] =
    read(content).map(_.sentences)

  /** The sentences, as `sentences` finds them, and the warnings: each table body row with more
    * cells than its header, whose extra cells aren't read.
    */
  def read(content: String): Either[String, MarkdownText] =
    val text = withoutByteOrderMark(content)
    CmarkGfm.records(text).flatMap(CmarkTree.decode).map { document =>
      val blocks = CmarkReader.read(document, text)
      MarkdownText(blocks.paragraphs.flatMap(Extraction.sentences), blocks.warnings)
    }

  /** A leading byte-order mark (U+FEFF) isn't text: commonmark.js and cmark drop it,
    * commonmark-java doesn't. Written as a code point, since scalafmt would turn the escape into
    * the character.
    */
  private def withoutByteOrderMark(content: String): String =
    if content.headOption.exists(_.toInt == 0xfeff) then content.drop(1) else content
