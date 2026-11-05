package io.github.tsnee.ears.markdown

/** Real line endings in decoded text. mdast decodes `&#10;` and `&#13;` into the characters they
  * name, which look like line endings in its text values but aren't in the source: commonmark-java
  * and cmark keep them as text. Comparing the decoded text with its source tells them apart.
  */
private[markdown] object LineEndings:

  private val ending = "\r\n|\r|\n".r

  /** Character references for a line feed or a carriage return, with what each decodes to. */
  private val reference = "&#0*10;|&#[xX]0*[aA];|&NewLine;|&#0*13;|&#[xX]0*[dD];".r

  private def decoded(found: String): String =
    if found.contains("13") || found.toLowerCase.endsWith("d;") then "\r" else "\n"

  /** `value`, decoded from `source`, split at the line endings that are in `source`. A line ending
    * that a character reference produced stays in its line. If the two don't line up (a reference
    * pair decoding to one CRLF, say), every line ending counts.
    */
  def lines(source: String, value: String): List[String] =
    val separators = ending.findAllMatchIn(value).toList
    val sourceLines = source.split("\r\n|\r|\n", -1).toList
    // How many line endings each source line's references add to the decoded text.
    val added = sourceLines.map(line =>
      ending
        .findAllMatchIn(
          reference.replaceAllIn(line, found => decoded(Option(found.matched).getOrElse("")))
        )
        .size
    )
    val real = added.dropRight(1).scanLeft(0)(_ + _ + 1).drop(1).map(_ - 1)
    if real.lastOption.exists(_ >= separators.size) then value.split("\r\n|\r|\n", -1).toList
    else
      val cuts = real.map(separators(_))
      val starts = 0 :: cuts.map(_.end)
      val ends = cuts.map(_.start) :+ value.length
      starts.zip(ends).map((from, to) => value.substring(from, to))
