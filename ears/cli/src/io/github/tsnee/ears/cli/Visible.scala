package io.github.tsnee.ears.cli

/** What text output and the messages on standard error show of characters a terminal or a
  * line-based tool would act on: escape sequences, bells, the C1 controls (CSI and NEL among them),
  * and Unicode's line and paragraph separators. Each becomes `\uXXXX`, as in the log lines, so a
  * requirement or a file name can't move the cursor, retitle the window, or split a finding across
  * lines. Backslashes stay as they are, so a fix can be copied back into the file; JSON output
  * keeps the exact text for tools that must tell a literal `\u001b` from the character.
  */
object Visible:

  /** Text for one line: every such character, line breaks included, as `\uXXXX`. */
  def escape(text: String): String = text.flatMap { c =>
    if actedOn(c) then f"\\u${c.toInt}%04x" else c.toString
  }

  private def actedOn(c: Char): Boolean =
    Character.isISOControl(c) || c.toInt == 0x2028 || c.toInt == 0x2029
