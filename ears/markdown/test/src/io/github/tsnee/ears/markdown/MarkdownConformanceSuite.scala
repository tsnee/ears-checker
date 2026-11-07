package io.github.tsnee.ears.markdown

import zio.test.*

/** The same fixtures, with the same expected sentences and lines, on every platform: the JVM
  * (commonmark-java), Node (mdast), and Scala Native (cmark). The expected results are what the
  * CLI's Markdown reader gave before the facade, checked against CommonMark.
  */
object MarkdownConformanceSuite extends ZIOSpecDefault:

  private def fixture(name: String, content: String, expected: Sentence*) =
    test(name)(assertTrue(Markdown.sentences(content) == Right(expected.toList)))

  def spec = suite("Markdown conformance")(
    suite("what's read")(
      fixture(
        "a list item is read; a heading and a fenced code block are not",
        "# Reminders\n\n- The scheduler SHALL list upcoming events.\n\n```\nSHALL inside code\n```\n",
        Sentence(3, "The scheduler SHALL list upcoming events.")
      ),
      fixture(
        "a sentence whose keyword is only inside inline code isn't read",
        "Write `SHALL` in uppercase.\n"
      ),
      fixture(
        "an HTML block isn't read",
        "<div>\nThe scheduler shall hide.\n</div>\n\nThe calendar shall sync.\n",
        Sentence(5, "The calendar shall sync.")
      ),
      fixture(
        "ATX and setext headings aren't read",
        "# The scheduler shall title\n\nThe calendar shall heading\n===\n\nThe report shall print.\n",
        Sentence(6, "The report shall print.")
      ),
      fixture(
        "a tab-indented line is code; a list item after a tab is read",
        "\tThe scheduler shall indent.\n\n-\tThe calendar shall sync.\n",
        Sentence(3, "The calendar shall sync.")
      ),
      fixture(
        "nested list items are read",
        "- Outer item.\n  - The scheduler shall list events.\n  - The calendar shall sync.\n",
        Sentence(2, "The scheduler shall list events."),
        Sentence(3, "The calendar shall sync.")
      ),
      fixture(
        "a block quote's paragraph is read",
        "> The scheduler shall quote.\n> The calendar shall sync.\n",
        Sentence(1, "The scheduler shall quote."),
        Sentence(2, "The calendar shall sync.")
      ),
      fixture(
        "the keyword is found in any letter case, as a whole word",
        "The scheduler SHALL list. The calendar Shall sync. The report shalls.\n",
        Sentence(1, "The scheduler SHALL list."),
        Sentence(1, "The calendar Shall sync.")
      ),
      fixture("an empty document has no sentences", "")
    ),
    suite("sentence text")(
      fixture(
        "sentences split at a period and whitespace; a soft line break is a space",
        "Reminders are useful. When a reminder is due,\nthe scheduler SHALL send a notice.\n",
        Sentence(1, "When a reminder is due, the scheduler SHALL send a notice.")
      ),
      fixture(
        "a lazy continuation line continues the list item's sentence",
        "- The scheduler shall\nlist events.\n",
        Sentence(1, "The scheduler shall list events.")
      ),
      fixture(
        "hard line breaks, by two spaces or a backslash, are spaces",
        "The scheduler shall list  \nevents.\n\nThe calendar shall\\\nsync.\n",
        Sentence(1, "The scheduler shall list events."),
        Sentence(4, "The calendar shall sync.")
      ),
      fixture(
        "emphasis and links keep their text",
        "The *scheduler* shall **list** events. The [calendar](https://example.com) shall sync.\n",
        Sentence(1, "The scheduler shall list events."),
        Sentence(1, "The calendar shall sync.")
      ),
      fixture(
        "an image's alt text is text, keyword included",
        "The scheduler ![shall show](icon.png) a calendar. The ![report](r.png) shall print.\n",
        Sentence(1, "The scheduler shall show a calendar."),
        Sentence(1, "The report shall print.")
      ),
      fixture(
        "inline HTML tags are dropped; the text between them is kept",
        "The scheduler <span>shall</span> list events. The <b>calendar</b> shall sync.\n",
        Sentence(1, "The scheduler shall list events."),
        Sentence(1, "The calendar shall sync.")
      ),
      fixture(
        "a reference link keeps its text",
        "The scheduler shall list [events][ref].\n\n[ref]: https://example.com\n",
        Sentence(1, "The scheduler shall list events.")
      ),
      fixture(
        "a code span across lines has a space for its line ending",
        "The `scheduler\nservice` shall list events. The calendar shall\nsync daily.\n",
        Sentence(1, "The `scheduler service` shall list events."),
        Sentence(2, "The calendar shall sync daily.")
      )
    ),
    suite("sentence lines")(
      fixture(
        "a sentence that starts with inline code is on its own line",
        "# Reminders\n\nIntro paragraph.\n\nMore intro text.\n\n- `ears-checker` shall check every line.\n",
        Sentence(7, "`ears-checker` shall check every line.")
      ),
      fixture(
        "escapes and entities earlier in the paragraph don't move a sentence",
        "Some \\*escaped\\* text &amp;amp; more.\nThe scheduler shall list events.\n",
        Sentence(2, "The scheduler shall list events.")
      ),
      fixture(
        "a sentence after a soft break in the same run of text is on the next line",
        "The intro is short.\nThe scheduler shall list events.\n",
        Sentence(2, "The scheduler shall list events.")
      ),
      fixture(
        "a sentence that starts in a code span across lines is on the span's first line",
        "Intro. `The\nscheduler` shall list events.\n",
        Sentence(1, "`The scheduler` shall list events.")
      ),
      fixture(
        "several sentences on several lines of one paragraph",
        "First note. The scheduler shall\nlist events. The calendar shall sync. Later\nnotes.\n",
        Sentence(1, "The scheduler shall list events."),
        Sentence(2, "The calendar shall sync.")
      ),
      fixture(
        "CRLF line endings",
        "The intro.\r\nThe scheduler shall list.\r\n",
        Sentence(2, "The scheduler shall list.")
      )
    ),
    // Found in review: each made one parser disagree with the others.
    suite("lines after constructs a parser misplaces")(
      fixture(
        "a backslash hard break",
        "Intro.\\\nThe scheduler shall list.\n",
        Sentence(2, "The scheduler shall list.")
      ),
      fixture(
        "a backslash hard break, with CRLF",
        "Intro.\\\r\nThe scheduler shall list.\r\n",
        Sentence(2, "The scheduler shall list.")
      ),
      fixture(
        "a link whose title is on the next line",
        "[link](u\n\"t\") x.\nThe scheduler shall list.\n",
        Sentence(3, "The scheduler shall list.")
      ),
      fixture(
        "a paragraph right after a reference definition",
        "[r]: /u\nThe scheduler shall list.\n",
        Sentence(2, "The scheduler shall list.")
      ),
      fixture(
        "a paragraph right after a reference definition, in a block quote",
        "> [r]: /u\n> The scheduler shall list.\n> The calendar shall\n> sync.\n",
        Sentence(2, "The scheduler shall list."),
        Sentence(3, "The calendar shall sync.")
      ),
      fixture(
        "a reference definition, then a code span across lines",
        "[r]: /u\nIntro `a\nb` c.\nThe scheduler shall list.\n",
        Sentence(4, "The scheduler shall list.")
      )
    ),
    suite("text a parser decodes differently")(
      fixture(
        "an image reference's alt text",
        "The ![report] shall print.\n\n[report]: i.png\n",
        Sentence(1, "The report shall print.")
      ),
      fixture(
        "the keyword in an image reference's alt text",
        "The ![shall show][r] x.\n\n[r]: i.png\n",
        Sentence(1, "The shall show x.")
      ),
      fixture(
        "alt text across lines",
        "Intro ![a\nshall b](i.png) x.\n",
        Sentence(1, "Intro a shall b x.")
      ),
      fixture(
        "a character reference for a line feed isn't a line ending",
        "Intro.&#10;The scheduler shall list.\n",
        Sentence(1, "The scheduler shall list.")
      ),
      fixture(
        "a character reference for a carriage return isn't a line ending",
        "Intro.&#13;The scheduler shall list.\n",
        Sentence(1, "The scheduler shall list.")
      ),
      fixture(
        "a character reference for a line feed stays in the sentence's text",
        "Intro&#10;the scheduler shall list.\n",
        Sentence(1, "Intro\nthe scheduler shall list.")
      ),
      fixture(
        "a leading byte-order mark isn't text",
        s"${0xfeff.toChar}The scheduler shall list.\n",
        Sentence(1, "The scheduler shall list.")
      )
    ),
    // Inline code comes back as a code span, written as CommonMark writes one; a backtick that
    // isn't code comes back escaped, so the classifier reads both as Markdown does.
    suite("inline code")(
      List(
        ("double backticks around a backtick", "``a ` b``", "``a ` b``"),
        ("padding is stripped, and written back", "`` `a ``", "`` `a ``"),
        ("a lone backtick in code", "`` ` ``", "`` ` ``"),
        ("escaped backticks are text", "\\`not code\\`", "\\`not code\\`"),
        ("runs that don't match are text", "``a`", "\\`\\`a\\`"),
        ("a code span of one space", "` `", "` `"),
        ("one space of padding is stripped from each side", "`  a  `", "` a `"),
        ("inline HTML takes precedence over code", "<span title=\"`\">`</span>", "\\`"),
        ("no escapes inside code", "`a\\`", "`a\\`"),
        (
          "a backslash before code is doubled, so the code stays code",
          "C:\\\\`a. b`",
          "C:\\\\`a. b`"
        ),
        ("a backslash before a backtick in text: both escaped", "\\\\\\`", "\\\\\\`"),
        ("other backslashes are left as they are", "C:\\\\x", "C:\\x"),
        ("code, dropped HTML, then code: one code span", "`a`<b></b>`` ` ``", "`` a` ``")
      ).map((name, written, read) =>
        fixture(
          name,
          s"The scheduler shall print $written now.\n",
          Sentence(1, s"The scheduler shall print $read now.")
        )
      ) ++ List(
        fixture(
          "a period inside code ends no sentence",
          "The checker shall write `Cannot read notes.txt. Check the path.` on failure.\n",
          Sentence(
            1,
            "The checker shall write `Cannot read notes.txt. Check the path.` on failure."
          )
        )
      )*
    ),
    // Parsers limit nesting differently, so the results needn't agree; none may crash.
    suite("deep nesting")(
      test("5,000 levels of emphasis: the sentence is still read"):
        val content = "*a " * 5000 + "The scheduler shall list." + " a*" * 5000 + "\n"
        assertTrue(
          Markdown.sentences(content).exists(_.exists(_.text.contains("The scheduler shall list.")))
        )
      ,
      test("10,000 nested block quotes: no crash"):
        val content = "> " * 10000 + "The scheduler shall list.\n"
        assertTrue(Markdown.sentences(content).exists(_.size <= 1))
    ),
    test("the platform's parser is found and named"):
      assertTrue(Markdown.parser().exists(_.nonEmpty))
  )
