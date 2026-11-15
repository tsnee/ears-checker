package io.github.tsnee.ears.cli

/** What the output and the messages on standard error show of the text they take from the input.
  * The checker reads English only, so every character outside the English character set is escaped:
  * printable ASCII, the Latin-1 letters and symbols (without the soft hyphen, which is invisible),
  * the combining accents (decomposed text spells `é` as `e` and one), and the typographic
  * punctuation word processors insert stay; everything else (controls, line separators,
  * bidirectional and other invisible format characters, emoji, other scripts, lone surrogates)
  * becomes `\uXXXX`, one per UTF-16 code unit. A requirement or a file name then can't move the
  * cursor, split a finding across lines, show it in another order, hide text, or pass a look-alike
  * letter for a Latin one, and every build writes the same bytes, since no platform's Unicode
  * tables are asked. The first `#` of two side by side is escaped too, so no output holds a CI
  * system's command marker (`##[` in GitHub Actions, found anywhere in a line; `##vso[`;
  * `##teamcity[`). Backslashes stay as they are, so a fix can be copied back into the file. Log
  * lines escape the same characters (`escaped`). JSON output goes through it whole: `\uXXXX` is a
  * JSON escape for the same character, and everything outside the document's strings is printable
  * ASCII.
  */
object Visible:

  /** Text for one line: every such character, line breaks included, as `\uXXXX`. */
  def escape(text: String): String =
    // JSON output passes its whole document, which most often needs nothing escaped.
    if text.indices.exists(escaped(text, _)) then
      val out = StringBuilder(text.length + 16)
      text.indices.foreach { i =>
        val c = text(i)
        if escaped(text, i) then out ++= code(c) else out += c
      }
      out.result()
    else text

  /** Whether the code unit at `i` is escaped: the one predicate for what's escaped, which log lines
    * (`Logging`) use too. It's outside the English character set, or it's a `#` followed by
    * another.
    */
  private[cli] def escaped(text: String, i: Int): Boolean =
    val c = text(i)
    !english(c) || (c == '#' && text.lift(i + 1).contains('#'))

  /** A code unit as `\uXXXX`, in four lowercase hexadecimal digits. */
  private[cli] def code(c: Char): String = f"\\u${c.toInt}%04x"

  /** Printable ASCII; U+00A0 to U+00FF without the soft hyphen (U+00AD); the combining accents
    * (U+0300 to U+036F); and U+2010 to U+2022 (hyphens, dashes, quotes, daggers, the bullet), the
    * ellipsis (U+2026), per mille (U+2030), primes (U+2032, U+2033), single angle quotes (U+2039,
    * U+203A), the euro sign (U+20AC), and the trade mark sign (U+2122).
    */
  private def english(c: Char): Boolean =
    val n = c.toInt
    (n >= 0x20 && n <= 0x7e) || (n >= 0xa0 && n <= 0xff && n != 0xad) ||
    (n >= 0x300 && n <= 0x36f) || (n >= 0x2010 && n <= 0x2022) || n == 0x2026 || n == 0x2030 || n == 0x2032 || n == 0x2033 ||
    n == 0x2039 || n == 0x203a || n == 0x20ac || n == 0x2122
