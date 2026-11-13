package io.github.tsnee.ears.markdown

import zio.test.*

/** The same fixtures, with the same expected sentences and lines, on every platform: the JVM
  * (commonmark-java), Node (commonmark.js), and Scala Native (cmark). The expected results are what
  * the CLI's Markdown reader gave before the facade, checked against CommonMark.
  */
object MarkdownConformanceSuite extends ZIOSpecDefault:

  private def fixture(name: String, content: String, expected: Sentence*) =
    test(name)(assertTrue(Markdown.sentences(content) == Right(expected.toList)))

  /** A fixture for `Markdown.read`: its sentences and its warnings. */
  private def readFixture(
      name: String,
      content: String,
      warnings: List[MarkdownWarning],
      expected: Sentence*
  ) =
    test(name)(assertTrue(Markdown.read(content) == Right(MarkdownText(expected.toList, warnings))))

  /** A two-column table whose one body row, on line 3, is `| A | <cell> |`. */
  private def oneRow(cell: String): String = s"| ID | Requirement |\n|---|---|\n| A | $cell |\n"

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
        "escapes on every line of a paragraph keep each sentence on its line",
        "Intro.\n\nThe \\<system\\> shall \\<a\\>.\nThe \\<system\\> shall \\<b\\>.\n" +
          "The \\<system\\> shall \\<c\\>.\n",
        Sentence(3, "The <system> shall <a>."),
        Sentence(4, "The <system> shall <b>."),
        Sentence(5, "The <system> shall <c>.")
      ),
      fixture(
        "inline HTML across lines keeps the text on each side apart",
        "The <span\nclass=x> a SHALL b. The c SHALL d.\nThe e SHALL f.\n",
        Sentence(1, "The  a SHALL b."),
        Sentence(2, "The c SHALL d."),
        Sentence(3, "The e SHALL f.")
      ),
      fixture(
        "an escaped first word after inline HTML across lines",
        "Intro. <span\nclass=x>\\*b\\* shall c.\n",
        Sentence(2, "*b* shall c.")
      ),
      fixture(
        "text decoded from an entity, then the same text written plainly, later",
        "Tom &amp; Jerry SHALL run.\nThe x SHALL y.\nTom & Jerry SHALL run.\n",
        Sentence(1, "Tom & Jerry SHALL run."),
        Sentence(2, "The x SHALL y."),
        Sentence(3, "Tom & Jerry SHALL run.")
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
    // GitHub's tables, read cell by cell. Each fixture's cells were checked against GitHub's
    // renderer (`gh api markdown -f mode=gfm`) on 2026-11-12, unless its comment says otherwise.
    suite("tables")(
      readFixture(
        "a table with outer pipes: each cell's sentence on its row's line",
        "Intro.\n\nMore.\n\n| Event | Response |\n|---|---|\n" +
          "| Payment fails | The billing service shall retry the charge. |\n" +
          "| Refund | The billing service shall notify the customer |\n",
        Nil,
        Sentence(7, "The billing service shall retry the charge."),
        Sentence(8, "The billing service shall notify the customer")
      ),
      readFixture(
        "a table without outer pipes",
        "Event | Response\n--- | ---\nPayment fails | The billing service shall retry the charge.\n",
        Nil,
        Sentence(3, "The billing service shall retry the charge.")
      ),
      readFixture(
        "a table right after a paragraph line ends the paragraph",
        "The billing service shall retry the charge.\n| Event | Response |\n|---|---|\n" +
          "| Refund | When a refund is issued the billing service shall notify the customer. |\n",
        Nil,
        Sentence(1, "The billing service shall retry the charge."),
        Sentence(4, "When a refund is issued the billing service shall notify the customer.")
      ),
      readFixture(
        "lines with pipes and no delimiter row are paragraph text",
        "The router shall forward | packets to the uplink.\nThe router shall drop | malformed frames.\n",
        Nil,
        Sentence(1, "The router shall forward | packets to the uplink."),
        Sentence(2, "The router shall drop | malformed frames.")
      ),
      readFixture(
        "a delimiter row with a different number of cells makes no table",
        "| Event | Response |\n|---|---|---|\n" +
          "| Refund | The billing service shall notify the customer. |\n",
        Nil,
        Sentence(
          1,
          "| Event | Response | |---|---|---| | Refund | The billing service shall notify the customer."
        )
      ),
      readFixture(
        "two requirements in one row are two sentences",
        "| Rule | Detail |\n|---|---|\n" +
          "| The scheduler shall list events. | The scheduler shall notify users |\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(3, "The scheduler shall notify users")
      ),
      readFixture(
        "a short row is read, with no warning",
        "| ID | Requirement | Note |\n|---|---|---|\n| A | The proxy shall reject the request. |\n",
        Nil,
        Sentence(3, "The proxy shall reject the request.")
      ),
      readFixture(
        "the third body row's sentence is on its line, after a heading",
        "# Billing\n| ID | Requirement |\n|---|---|\n| A | Notes. |\n| B | More notes. |\n" +
          "| C | The billing service shall retry the charge. |\n",
        Nil,
        Sentence(6, "The billing service shall retry the charge.")
      ),
      readFixture(
        "a header cell is read too",
        "| The scheduler shall list events. |\n|---|\n",
        Nil,
        Sentence(1, "The scheduler shall list events.")
      ),
      readFixture(
        "a header line with no pipe over a one-cell delimiter row is a one-column table",
        "Requirement\n| --- |\n| The scheduler shall list events. |\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      readFixture(
        "a table in a list item and one in a block quote",
        "- Rules:\n  | ID | Requirement |\n  |---|---|\n  | A | The scheduler shall list events. |\n\n" +
          "> | ID | Requirement |\n> |---|---|\n> | B | The calendar shall sync. |\n",
        Nil,
        Sentence(4, "The scheduler shall list events."),
        Sentence(8, "The calendar shall sync.")
      ),
      readFixture(
        "CRLF line endings",
        "| ID | Requirement |\r\n|---|---|\r\n| A | The proxy shall log x \\| y. |\r\n",
        Nil,
        Sentence(3, "The proxy shall log x | y.")
      ),
      readFixture(
        "escapes and references on every row keep each sentence on its row",
        "| ID | Requirement |\n|---|---|\n| A | The proxy shall log x \\| y. |\n" +
          "| B | The proxy shall log x &#124; y. |\n| C | The proxy shall reject `x \\| y`. |\n",
        Nil,
        Sentence(3, "The proxy shall log x | y."),
        Sentence(4, "The proxy shall log x | y."),
        Sentence(5, "The proxy shall reject `x | y`.")
      ),
      readFixture(
        "an escaped pipe stays in its cell",
        oneRow("The proxy shall log \\| escaped pipes."),
        Nil,
        Sentence(3, "The proxy shall log | escaped pipes.")
      ),
      readFixture(
        "an escaped pipe in inline code stays in its cell, unescaped",
        oneRow("The proxy shall reject `a \\| b` headers."),
        Nil,
        Sentence(3, "The proxy shall reject `a | b` headers.")
      ),
      readFixture(
        "a character reference for a pipe stays in its cell",
        oneRow("The proxy shall log &#124; entities."),
        Nil,
        Sentence(3, "The proxy shall log | entities.")
      ),
      // GitHub shows the cell as "The proxy shall reject `a", its lone backtick as text.
      readFixture(
        "a bare pipe in inline code splits the row, as GitHub does, with a warning",
        oneRow("The proxy shall reject `a|b` headers."),
        List(MarkdownWarning.ExtraCells(line = 3, cells = 3, headerCells = 2)),
        Sentence(3, "The proxy shall reject \\`a")
      ),
      readFixture(
        "a row's cells beyond the header's are unread",
        oneRow("one | The billing service shall refund the order. | two"),
        List(MarkdownWarning.ExtraCells(line = 3, cells = 4, headerCells = 2))
      ),
      readFixture(
        "a row with extra cells gets a warning with its line and counts",
        "# Billing\n\nRefunds follow.\n\n| ID | Requirement |\n|---|---|\n| A | Notes. |\n" +
          "| B | More notes. |\n| C | one | two | three |\n",
        List(MarkdownWarning.ExtraCells(line = 9, cells = 4, headerCells = 2))
      ),
      // GitHub splits the cell at the pipe in the link's destination, shows the rest of the cell
      // ("The proxy shall [log](https://example.com/a") as text, and hides "b) requests.".
      readFixture(
        "a pipe in a link's destination splits the cell, as GitHub does, with a warning",
        oneRow("The proxy shall [log](https://example.com/a|b) requests."),
        List(MarkdownWarning.ExtraCells(line = 3, cells = 3, headerCells = 2)),
        Sentence(3, "The proxy shall [log](https://example.com/a")
      ),
      // Checked against GitHub's renderer on 2026-11-12: one cell, the reference shown literally.
      readFixture(
        "a character reference in inline code isn't a pipe, beside an escaped pipe in text",
        oneRow("The proxy shall log x \\| y and reject `&#124;`."),
        Nil,
        Sentence(3, "The proxy shall log x | y and reject `&#124;`.")
      ),
      // GitHub splits rows before it reads inline code, so line 1 is a paragraph ("Intro `a") and
      // lines 2 to 4 a table with "The proxy shall reject y." in its body.
      readFixture(
        "inline code that would cross into a table's header row: rows are split first",
        "Intro `a\nb` | c\n|---|---|\n| x | The proxy shall reject y. |\n",
        Nil,
        Sentence(4, "The proxy shall reject y.")
      ),
      // Checked against GitHub's renderer on 2026-11-12: a header cell that is only a reference
      // for a space is a cell, not the empty one an outer pipe leaves, so the header has two cells.
      readFixture(
        "a first header cell that is only a reference for a space is a cell",
        "&#32;| Requirement |\n|---|---|\n| A | The proxy shall reject the request. |\n",
        Nil,
        Sentence(3, "The proxy shall reject the request.")
      ),
      readFixture(
        "a last header cell that is only a reference for a space is a cell",
        "| Requirement |&#32;\n|---|---|\n| The proxy shall reject the request. | B |\n",
        Nil,
        Sentence(3, "The proxy shall reject the request.")
      )
    ),
    // Where GitHub ends a table inside what CommonMark reads as one paragraph, and where a
    // paragraph's raw lines start: each fixture checked against GitHub's renderer
    // (`gh api markdown -f mode=gfm`) on 2026-11-12, its result in the comment above it.
    suite("table end")(
      // GitHub: one row, "A" and the sentence; the empty third cell isn't shown.
      readFixture(
        "an empty extra cell gets no warning",
        oneRow("The proxy shall log the request. |"),
        Nil,
        Sentence(3, "The proxy shall log the request.")
      ),
      // GitHub: the table in the block quote, then a paragraph "The calendar shall sync. | note".
      readFixture(
        "a lazy line after a table in a block quote is a paragraph",
        "> | ID | Requirement |\n> |---|---|\n> | A | The scheduler shall list events. |\n" +
          "The calendar shall sync. | note\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(4, "The calendar shall sync.")
      ),
      // GitHub: the table in the block quote, then a paragraph
      // "The calendar shall sync. | note | more".
      readFixture(
        "a lazy line with more pipes than the header after a table in a block quote: no warning",
        "> | ID | Requirement |\n> |---|---|\n> | A | The scheduler shall list events. |\n" +
          "The calendar shall sync. | note | more\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(4, "The calendar shall sync.")
      ),
      // GitHub: the table in the block quote, then a second table, outside it, whose row B has an
      // extra cell.
      readFixture(
        "a table after a lazy line ends the first table: its rows read, with its warnings",
        "> | ID | Requirement |\n> |---|---|\n> | A | The scheduler shall list events. |\n" +
          "Note | Detail\n---|---\nB | The calendar shall sync. | extra\n",
        List(MarkdownWarning.ExtraCells(line = 6, cells = 3, headerCells = 2)),
        Sentence(3, "The scheduler shall list events."),
        Sentence(6, "The calendar shall sync.")
      ),
      // GitHub: the table in the block quote, then a paragraph whose link is resolved.
      readFixture(
        "a reference link in a lazy line after a table takes the document's definition",
        "> | ID | Requirement |\n> |---|---|\n> | A | The scheduler shall list events. |\n" +
          "[The calendar][c] shall sync. | note\n\n[c]: https://example.com/calendar\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(4, "The calendar shall sync.")
      ),
      // GitHub: the table in the list item, then a paragraph "The calendar shall sync. | a | b".
      readFixture(
        "a lazy line after a table in a list item is a paragraph",
        "- | ID | Requirement |\n  |---|---|\n  | A | The scheduler shall list events. |\n" +
          "The calendar shall sync. | a | b\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(4, "The calendar shall sync.")
      ),
      // GitHub: the table, then a code block "The proxy shall log | requests.".
      readFixture(
        "an indented code line after a table isn't read",
        oneRow("The scheduler shall list events.") + "    The proxy shall log | requests.\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table, then a list starting at 2 with "The calendar shall sync. | a | b".
      readFixture(
        "a list item numbered 2 ends a table and is read as a list item",
        oneRow("The scheduler shall list events.") + "2. The calendar shall sync. | a | b\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(4, "The calendar shall sync.")
      ),
      // GitHub: the table, an empty list item, then a paragraph "The calendar shall sync. | a | b".
      // (`+` rather than `-`: the parsers read `-` alone as a setext heading's underline, which
      // "a single dash under a table is an empty list item" covers.)
      readFixture(
        "an empty list item ends a table",
        oneRow("The scheduler shall list events.") + "+\nThe calendar shall sync. | a | b\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync.")
      ),
      // GitHub: the table, then an HTML block (`<span>` and the next line).
      readFixture(
        "an HTML block line ends a table, and the HTML isn't read",
        oneRow("The scheduler shall list events.") +
          "<span class=\"note\">\nThe proxy shall log | requests.\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: "Rules:", then the table, then an HTML block, all in the list item.
      readFixture(
        "an HTML block line ends a table in a list item",
        "- Rules:\n  | ID | Requirement |\n  |---|---|\n  | A | The scheduler shall list events. |\n" +
          "  <span>\n  The proxy shall log | x | y.\n",
        Nil,
        Sentence(4, "The scheduler shall list events.")
      ),
      // GitHub: one paragraph of the three lines, no table.
      readFixture(
        "a delimiter row indented four columns isn't one",
        "| ID | Requirement |\n    |---|---|\n| A | The scheduler shall list events. |\n",
        Nil,
        Sentence(1, "| ID | Requirement | |---|---| | A | The scheduler shall list events.")
      ),
      // GitHub: a one-column table whose header is the image.
      readFixture(
        "an image alone is a header",
        "![](a.png)\n| --- |\n| The scheduler shall list events. |\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: a paragraph with the image, then a table whose first header cell is
      // "2) The calendar shall sync.". (With "2." the sentence would end at the period.)
      readFixture(
        "a list marker on a paragraph's second line, after an image, isn't stripped",
        "![](a.png)\n2) The calendar shall sync. | Note |\n|---|---|\n| A | B |\n",
        Nil,
        Sentence(2, "2) The calendar shall sync.")
      ),
      // A known difference, accepted by the owner: GitHub shows the definition as paragraph text,
      // "[s]: https://example.com/s The calendar shall sync.", since the table interrupts the
      // paragraph before it becomes a definition (as in "a definition directly above a table");
      // the parsers take it as a definition. What this fixture holds is that the paragraph's text
      // starts after the definition, and the table after that.
      readFixture(
        "a definition, then text, over a table",
        "[s]: https://example.com/s\nThe calendar shall sync.\n| ID | Requirement |\n|---|---|\n" +
          "| A | The scheduler shall list events. |\n",
        Nil,
        Sentence(2, "The calendar shall sync."),
        Sentence(5, "The scheduler shall list events.")
      )
    ),
    // A line of `-` or `=` directly under a table, which the parsers read as a setext heading's
    // underline and GitHub as the table's next line: each fixture checked against GitHub's
    // renderer (`gh api markdown -f mode=gfm`) on 2026-11-12, its result in the comment above it.
    suite("underline after a table")(
      // GitHub: the table with rows A and B, then a rule.
      readFixture(
        "a rule under a table ends it",
        oneRow("The scheduler shall list events.") + "| B | The calendar shall sync. |\n---\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(4, "The calendar shall sync.")
      ),
      // GitHub: the table with rows A and "===" (its second cell empty).
      readFixture(
        "an equals line under a table is a body row",
        oneRow("The scheduler shall list events.") + "===\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: a paragraph "The report shall print.", then the table with rows A and "===".
      readFixture(
        "paragraph lines before an underlined table are a paragraph",
        "The report shall print.\n" + oneRow("The scheduler shall list events.") + "===\n",
        Nil,
        Sentence(1, "The report shall print."),
        Sentence(4, "The scheduler shall list events.")
      ),
      // GitHub: in the block quote, the table, then a rule.
      readFixture(
        "a rule under a table in a block quote",
        "> | ID | Requirement |\n> |---|---|\n> | A | The scheduler shall list events. |\n> ---\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: in the list item, the table, then a rule.
      readFixture(
        "a rule under a table in a list item",
        "- | ID | Requirement |\n  |---|---|\n  | A | The scheduler shall list events. |\n  ---\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table with no body rows, then a rule.
      readFixture(
        "a rule under a table with no body rows",
        "| ID | The scheduler shall list events. |\n|---|---|\n---\n",
        Nil,
        Sentence(1, "The scheduler shall list events.")
      ),
      // GitHub: the table with one body row, "===" (its second cell empty).
      readFixture(
        "an equals line under a table with no body rows is its body row",
        "| ID | The scheduler shall list events. |\n|---|---|\n===\n",
        Nil,
        Sentence(1, "The scheduler shall list events.")
      ),
      // GitHub: the table, then a list with one empty item.
      readFixture(
        "a single dash under a table is an empty list item",
        oneRow("The scheduler shall list events.") + "-\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table with rows A and "--" (its second cell empty).
      readFixture(
        "two dashes under a table are a body row",
        oneRow("The scheduler shall list events.") + "--\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table, then a rule.
      readFixture(
        "a rule indented three spaces under a table ends it",
        oneRow("The scheduler shall list events.") + "   ---\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table, then a rule.
      readFixture(
        "a rule with a trailing space under a table ends it",
        oneRow("The scheduler shall list events.") + "--- \n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table, then a rule. (Every parser ends the paragraph here already.)
      readFixture(
        "a spaced rule under a table ends it",
        oneRow("The scheduler shall list events.") + "- - -\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table, then a rule. (Every parser ends the paragraph here already.)
      readFixture(
        "a rule of two dashes and a spaced one under a table ends it",
        oneRow("The scheduler shall list events.") + "-- -\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table, then a rule. (Every parser ends the paragraph here already.)
      readFixture(
        "a rule of asterisks under a table ends it",
        oneRow("The scheduler shall list events.") + "***\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: the table, then a code block "---". (Every parser reads the line as the
      // paragraph's, not an underline, already.)
      readFixture(
        "dashes indented four spaces under a table are code",
        oneRow("The scheduler shall list events.") + "    ---\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // GitHub: an h2, "| The scheduler shall list events. |": no delimiter row, so no table.
      readFixture(
        "a one-column line over a rule is a heading, and isn't read",
        "| The scheduler shall list events. |\n---\n",
        Nil
      ),
      // GitHub: the table with rows A, "===", B, and C, C's third cell not shown.
      readFixture(
        "rows after an equals line are the table's",
        oneRow("The scheduler shall list events.") + "===\n| B | The calendar shall sync. |\n" +
          "| C | The report shall print. | extra |\n",
        List(MarkdownWarning.ExtraCells(line = 6, cells = 3, headerCells = 2)),
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync."),
        Sentence(6, "The report shall print.")
      ),
      // GitHub: the table with rows A, "=", B, "==", and C.
      readFixture(
        "a row underlined again after an equals row: the table goes on",
        oneRow("The scheduler shall list events.") + "=\n| B | The calendar shall sync. |\n==\n" +
          "| C | The report shall print. |\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync."),
        Sentence(7, "The report shall print.")
      ),
      // GitHub: the table A in the block quote, a paragraph whose link is resolved, then the table
      // with rows B and "===". (The lines after the first table, read again with the document for
      // its definition, end in a heading, which cmark ends on the blank line after it.)
      readFixture(
        "an underlined table in the lines after a table, with a reference link",
        "> | ID | Requirement |\n> |---|---|\n> | A | The scheduler shall list events. |\n" +
          "[The calendar][c] shall sync.\n| ID | Requirement |\n|---|---|\n" +
          "| B | The report shall print. |\n===\n\n[c]: https://example.com/calendar\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(4, "The calendar shall sync."),
        Sentence(7, "The report shall print.")
      ),
      // Found by the code review, each checked against GitHub's renderer on 2026-11-13.
      // GitHub: the table with rows A and "===", then a block quote "The calendar shall sync.".
      readFixture(
        "a block quote after an equals line ends the table",
        oneRow("The scheduler shall list events.") + "===\n> The calendar shall sync.\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync.")
      ),
      // GitHub: the table with rows A, "===", and B, then a block quote "The report shall print.".
      readFixture(
        "a block quote after the rows after an equals line ends the table",
        oneRow("The scheduler shall list events.") + "===\n| B | The calendar shall sync. |\n" +
          "> The report shall print.\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync."),
        Sentence(6, "The report shall print.")
      ),
      // GitHub: the table with rows A, "===", and B, then an h1 "The report shall print.".
      readFixture(
        "a heading after the rows after an equals line ends the table, and isn't read",
        oneRow("The scheduler shall list events.") + "===\n| B | The calendar shall sync. |\n" +
          "# The report shall print.\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync.")
      ),
      // GitHub: the table with rows A, "===", and B, then a code block "The report shall print.".
      readFixture(
        "a code fence after the rows after an equals line ends the table, and isn't read",
        oneRow("The scheduler shall list events.") + "===\n| B | The calendar shall sync. |\n" +
          "```\nThe report shall print.\n```\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync.")
      ),
      // GitHub: a paragraph "===", the table with row A, then a rule: the definition is
      // paragraph text there, so `===` is the paragraph's, not an underline.
      readFixture(
        "a definition and an equals line over a table",
        "[a]: /url\n===\n" + oneRow("The scheduler shall list events.") + "---\n",
        Nil,
        Sentence(5, "The scheduler shall list events.")
      ),
      // GitHub: the table with rows A, "===", "[b]: /url", "==", B, "==", and C. (The parsers read
      // a definition, then a heading whose text is "==", over the rows from line 7.)
      readFixture(
        "a definition and an equals line in the rows after an equals line",
        oneRow("The scheduler shall list events.") + "===\n[b]: /url\n==\n" +
          "| B | The calendar shall sync. |\n==\n| C | The report shall print. |\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(7, "The calendar shall sync."),
        Sentence(9, "The report shall print.")
      ),
      // GitHub: in the list item, the table and a rule; then a paragraph "The calendar shall
      // sync.".
      readFixture(
        "a rule under a table in a list item indented with a tab",
        "- | ID | Requirement |\n\t|---|---|\n\t| A | The scheduler shall list events. |\n\t---\n" +
          "The calendar shall sync.\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync.")
      ),
      // GitHub: in the block quote, the table, a rule, and a paragraph "The calendar shall sync.".
      readFixture(
        "a rule under a table indented with a tab in a block quote",
        ">\t| ID | Requirement |\n>\t|---|---|\n>\t| A | The scheduler shall list events. |\n" +
          ">\t---\n> The calendar shall sync.\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync.")
      ),
      // GitHub: in the list item, a paragraph "Rules:", the table, a rule, and a paragraph "The
      // calendar shall sync.".
      readFixture(
        "a rule under a table indented three spaces in an ordered list item",
        "1. Rules:\n\n   | ID | Requirement |\n   |---|---|\n" +
          "   | A | The scheduler shall list events. |\n   ---\n   The calendar shall sync.\n",
        Nil,
        Sentence(5, "The scheduler shall list events."),
        Sentence(7, "The calendar shall sync.")
      ),
      // GitHub: in the inner list item, the table with rows A, "===", and B.
      readFixture(
        "rows after an equals line in a nested list item",
        "1. Rules:\n   - | ID | Requirement |\n     |---|---|\n" +
          "     | A | The scheduler shall list events. |\n     ===\n" +
          "     | B | The calendar shall sync. |\n",
        Nil,
        Sentence(4, "The scheduler shall list events."),
        Sentence(6, "The calendar shall sync.")
      ),
      // GitHub: the table, a rule, then a paragraph "The calendar shall sync.".
      readFixture(
        "a paragraph after a rule under a table",
        oneRow("The scheduler shall list events.") + "---\nThe calendar shall sync.\n",
        Nil,
        Sentence(3, "The scheduler shall list events."),
        Sentence(5, "The calendar shall sync.")
      )
    ),
    // A cell is inline text, read as GitHub reads it: each fixture checked against GitHub's
    // renderer (`gh api markdown -f mode=gfm`) on 2026-11-12.
    suite("table cells as inline text")(
      readFixture(
        "cells that look like a list item, a heading, or a block quote are inline text",
        "| Step | Note |\n|---|---|\n" +
          "| 1) The scheduler shall retry the job. | - The calendar shall sync. |\n" +
          "| # The scheduler shall list events. | > The calendar shall notify users. |\n",
        Nil,
        Sentence(3, "1) The scheduler shall retry the job."),
        Sentence(3, "- The calendar shall sync."),
        Sentence(4, "# The scheduler shall list events."),
        Sentence(4, "> The calendar shall notify users.")
      ),
      readFixture(
        "emphasis, inline code, and links in a cell",
        "| ID | Requirement |\n|---|---|\n" +
          "| A | The *scheduler* shall `list` events. The [calendar](https://example.com) " +
          "shall sync. |\n",
        Nil,
        Sentence(3, "The scheduler shall `list` events."),
        Sentence(3, "The calendar shall sync.")
      ),
      readFixture(
        "a reference link in a cell takes the document's definition",
        "| ID | Requirement |\n|---|---|\n| A | [The scheduler][s] shall list events. |\n\n" +
          "[s]: https://example.com/scheduler\n",
        Nil,
        Sentence(3, "The scheduler shall list events.")
      ),
      // A known difference, accepted by the owner (2026-11-12): directly above a table, with no
      // blank line, GitHub reads `[s]: …` as paragraph text (the table interrupts it before it
      // becomes a definition) and shows `[The scheduler][s]` unresolved; all three parsers take it
      // as a definition, so the reader resolves the link. Checked on GitHub's renderer 2026-11-12.
      readFixture(
        "a definition directly above a table resolves the cell's link, unlike GitHub",
        "[s]: https://example.com/scheduler\n| ID | Requirement |\n|---|---|\n" +
          "| A | [The scheduler][s] shall list events. |\n",
        Nil,
        Sentence(4, "The scheduler shall list events.")
      ),
      // GitHub keeps the indented line before the table as paragraph text, not a list item.
      readFixture(
        "an indented line before a table that looks like a list item stays paragraph text",
        "Intro text\n    - The scheduler shall keep this line.\n| ID | Requirement |\n|---|---|\n" +
          "| A | The calendar shall sync. |\n",
        Nil,
        Sentence(1, "Intro text - The scheduler shall keep this line."),
        Sentence(5, "The calendar shall sync.")
      ),
      readFixture(
        "a table in a list item inside a block quote, after text and on the item's first line",
        "> - Rules:\n>   | ID | Requirement |\n>   |---|---|\n" +
          ">   | A | The scheduler shall list events. |\n\n" +
          "> - | ID | Requirement |\n>   |---|---|\n>   | B | The calendar shall sync. |\n",
        Nil,
        Sentence(4, "The scheduler shall list events."),
        Sentence(8, "The calendar shall sync.")
      )
    ),
    // Backslashes before a pipe, and the references that decode to one, each checked against
    // GitHub's renderer on 2026-11-12: a pipe after any backslash stays in its cell, one backslash
    // fewer; a reference after an odd number of backslashes is text.
    suite("pipes in table cells")(
      List(
        ("one backslash in text", "x \\| y", "x | y"),
        ("two backslashes in text", "x \\\\| y", "x | y"),
        ("three backslashes in text", "x \\\\\\| y", "x \\| y"),
        ("one backslash in inline code", "`x \\| y`", "`x | y`"),
        ("two backslashes in inline code", "`x \\\\| y`", "`x \\| y`"),
        ("three backslashes in inline code", "`x \\\\\\| y`", "`x \\\\| y`"),
        ("a decimal reference", "x &#124; y", "x | y"),
        ("a decimal reference with leading zeros", "x &#0124; y", "x | y"),
        ("a hexadecimal reference", "x &#x7c; y", "x | y"),
        ("a hexadecimal reference, uppercase, with leading zeros", "x &#X0007C; y", "x | y"),
        ("&vert;", "x &vert; y", "x | y"),
        ("&verbar;", "x &verbar; y", "x | y"),
        ("&VerticalLine;", "x &VerticalLine; y", "x | y"),
        ("an escaped reference is text", "x \\&#124; y", "x &#124; y"),
        ("a reference after an escaped backslash", "x \\\\&#124; y", "x \\| y")
      ).map((name, written, read) =>
        readFixture(
          name,
          oneRow(s"The proxy shall log $written here."),
          Nil,
          Sentence(3, s"The proxy shall log $read here.")
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
    // Meaningful on Scala Native, where the parser is an installed cmark found when it runs; on the
    // JVM and Node the parser is a dependency, and the name is a constant.
    test("the platform's parser is found and named"):
      assertTrue(Markdown.parser().exists(_.nonEmpty))
  )
