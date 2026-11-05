package io.github.tsnee.ears.markdown

/** A run of a paragraph's text: inline code or not, and the 1-based line it starts on. A line break
  * inside a paragraph is a piece of one space with no line of its own.
  */
final case class Piece(text: String, code: Boolean, line: Option[Int]) derives CanEqual

/** A paragraph, list items included, and the 1-based line it starts on. */
final case class Paragraph(line: Int, pieces: List[Piece]) derives CanEqual

/** A requirement sentence and the 1-based line it starts on. */
final case class Sentence(line: Int, text: String) derives CanEqual

/** Requirement sentences in Markdown, the same on every platform: the platform's parser
  * (`MarkdownParser`, one per platform) gives the paragraphs, and `Extraction`, shared, finds the
  * sentences in them.
  */
object Markdown:

  /** Each sentence of each paragraph, list items included, that contains the modal keyword outside
    * inline code, with the line it starts on. Headings, code blocks, and HTML blocks have no
    * paragraphs, so nothing in them is read.
    */
  def sentences(content: String): List[Sentence] =
    MarkdownParser.paragraphs(withoutByteOrderMark(content)).flatMap(Extraction.sentences)

  /** A leading byte-order mark (U+FEFF) isn't text: mdast and cmark drop it, commonmark-java
    * doesn't. Written as a code point, since scalafmt would turn the escape into the character.
    */
  private def withoutByteOrderMark(content: String): String =
    if content.headOption.exists(_.toInt == 0xfeff) then content.drop(1) else content
