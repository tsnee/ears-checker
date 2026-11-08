package io.github.tsnee.ears.markdown

/** A run of a paragraph's text: inline code or not, and the 1-based line it starts on. A line break
  * inside a paragraph is a piece of one space with no line of its own.
  */
final case class Piece(text: String, code: Boolean, line: Option[Int]) derives CanEqual

/** A paragraph, list items included, and the 1-based line it starts on. */
final case class Paragraph(line: Int, pieces: List[Piece]) derives CanEqual

/** A requirement sentence and the 1-based line it starts on. */
final case class Sentence(line: Int, text: String) derives CanEqual

/** Why Markdown can't be read here (only on Scala Native, without a usable cmark): the message for
  * the user, and each library tried, for the log.
  */
final case class MarkdownUnavailable(message: String, attempts: List[String]) derives CanEqual

/** Requirement sentences in Markdown, the same on every platform: the platform's parser
  * (`MarkdownParser`, one per platform) gives the paragraphs, and `Extraction`, shared, finds the
  * sentences in them.
  */
object Markdown:

  /** The parser that reads Markdown on this platform, for the log; or why there's none. */
  def parser(): Either[MarkdownUnavailable, String] = MarkdownParser.name()

  /** Each sentence of each paragraph, list items included, that contains the modal keyword outside
    * inline code, with the line it starts on. Headings, code blocks, and HTML blocks have no
    * paragraphs, so nothing in them is read. Fails only on Scala Native, saying what to install,
    * when no usable cmark is found.
    */
  def sentences(content: String): Either[String, List[Sentence]] =
    MarkdownParser.paragraphs(withoutByteOrderMark(content)).map(_.flatMap(Extraction.sentences))

  /** A leading byte-order mark (U+FEFF) isn't text: commonmark.js and cmark drop it,
    * commonmark-java doesn't. Written as a code point, since scalafmt would turn the escape into
    * the character.
    */
  private def withoutByteOrderMark(content: String): String =
    if content.headOption.exists(_.toInt == 0xfeff) then content.drop(1) else content
