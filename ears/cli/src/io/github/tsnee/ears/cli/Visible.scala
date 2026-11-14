package io.github.tsnee.ears.cli

/** What the output and the messages on standard error show of characters a terminal or a line-based
  * tool would act on: escape sequences, bells, the C1 controls (CSI and NEL among them), and
  * Unicode's line and paragraph separators; and of lone surrogates, which each platform's UTF-8
  * encoder would replace with a different character. Each becomes `\uXXXX`, as in the log lines, so
  * a requirement or a file name can't move the cursor, retitle the window, or split a finding
  * across lines, and every build writes the same bytes. Backslashes stay as they are, so a fix can
  * be copied back into the file. JSON output goes through it whole: `\uXXXX` is a JSON escape for
  * the same character, and everything outside the document's strings is printable ASCII.
  */
object Visible:

  /** Text for one line: every such character, line breaks included, as `\uXXXX`. A surrogate pair
    * (an emoji) stays as it is.
    */
  def escape(text: String): String =
    // JSON output passes its whole document, which most often needs nothing escaped.
    if text.indices.exists(escaped(text, _)) then
      val out = StringBuilder(text.length + 16)
      text.indices.foreach { i =>
        val c = text(i)
        if escaped(text, i) then out ++= f"\\u${c.toInt}%04x" else out += c
      }
      out.result()
    else text

  private def escaped(text: String, i: Int): Boolean = actedOn(text(i)) || lone(text, i)

  private def actedOn(c: Char): Boolean =
    Character.isISOControl(c) || c.toInt == 0x2028 || c.toInt == 0x2029

  /** Whether the code unit at `i` is a surrogate that isn't half of a pair: a high one not followed
    * by a low one, or a low one not after a high one.
    */
  private def lone(text: String, i: Int): Boolean =
    val c = text(i)
    if Character.isHighSurrogate(c) then !text.lift(i + 1).exists(Character.isLowSurrogate)
    else Character.isLowSurrogate(c) && !text.lift(i - 1).exists(Character.isHighSurrogate)
