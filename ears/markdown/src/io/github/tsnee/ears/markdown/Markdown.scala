package io.github.tsnee.ears.markdown

/** A run of a paragraph's text: inline code or not, and the 1-based line it starts on. A line break
  * inside a paragraph is a piece of one space with no line of its own.
  */
final case class Piece(text: String, code: Boolean, line: Option[Int]) derives CanEqual

/** A paragraph, list items included, and the 1-based line its block starts on: from a parser, the
  * line of the first link reference definition that opens the block, if one does.
  */
final case class Paragraph(line: Int, pieces: List[Piece]) derives CanEqual

/** A paragraph as a platform's parser gives it, with the 1-based line it ends on, which every
  * parser knows from the paragraph's block: tables are read from the paragraph's raw lines.
  */
private[markdown] final case class ParsedParagraph(paragraph: Paragraph, lastLine: Int)
    derives CanEqual

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

/** Why Markdown can't be read here (only on Scala Native, without a usable cmark): the message for
  * the user, and each library tried, for the log.
  */
final case class MarkdownUnavailable(message: String, attempts: List[String]) derives CanEqual

/** Requirement sentences in Markdown, the same on every platform: the platform's parser
  * (`MarkdownParser`, one per platform) gives the paragraphs, `Tables`, shared, reads the GitHub
  * tables in them cell by cell, and `Extraction`, shared, finds the sentences.
  */
object Markdown:

  /** The parser that reads Markdown on this platform, for the log; or why there's none. */
  def parser(): Either[MarkdownUnavailable, String] = MarkdownParser.name()

  /** Each sentence of each paragraph and table cell, list items included, that contains the modal
    * keyword outside inline code, with the line it starts on. Headings, code blocks, and HTML
    * blocks have no paragraphs, so nothing in them is read. Fails only on Scala Native, saying what
    * to install, when no usable cmark is found.
    */
  def sentences(content: String): Either[String, List[Sentence]] =
    read(content).map(_.sentences)

  /** The sentences, as `sentences` finds them, and the warnings: each table body row with more
    * cells than its header, whose extra cells aren't read.
    */
  def read(content: String): Either[String, MarkdownText] =
    val text = withoutByteOrderMark(content)
    MarkdownParser
      .loaded()
      .flatMap(parse => Tables.read(parse(text), text, document => Right(parse(document))))
      .map(blocks => MarkdownText(blocks.paragraphs.flatMap(Extraction.sentences), blocks.warnings))

  /** A leading byte-order mark (U+FEFF) isn't text: commonmark.js and cmark drop it,
    * commonmark-java doesn't. Written as a code point, since scalafmt would turn the escape into
    * the character.
    */
  private def withoutByteOrderMark(content: String): String =
    if content.headOption.exists(_.toInt == 0xfeff) then content.drop(1) else content
