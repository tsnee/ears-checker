package io.github.tsnee.ears.cli

import zio.test.*

/** OpenSpec's Markdown, read by OpenSpec 2.0.0's own line rules: the same requirements and text as
  * `openspec show --json`, each with the line its text starts on.
  */
object OpenSpecMarkdownSuite extends ZIOSpecDefault:

  private def read(lines: String*): List[Statement] =
    OpenSpecMarkdown.statements(lines.mkString("\n") + "\n")

  private val scenario = List("#### Scenario: One", "", "- **WHEN** x", "- **THEN** y")

  def spec = suite("OpenSpecMarkdown")(
    suite("a spec")(
      test("each requirement in the Requirements section, at the line its text starts"):
        val spec = List(
          "# Scheduling Specification",
          "",
          "## Purpose",
          "",
          "The scheduler lists events.",
          "",
          "## Requirements",
          "",
          "### Requirement: Upcoming",
          "",
          "The scheduler SHALL list upcoming events."
        ) ++ scenario ++ List("", "### Requirement: Past", "The scheduler SHALL hide past events.")
        assertTrue(
          read(spec*) == List(
            Statement(11, "The scheduler SHALL list upcoming events."),
            Statement(18, "The scheduler SHALL hide past events.")
          )
        )
      ,
      test("the section's title in any case, at any level, with a closing run of number signs"):
        assertTrue(
          read("# requirements #", "## Upcoming", "The scheduler SHALL list events.") ==
            List(Statement(3, "The scheduler SHALL list events."))
        )
      ,
      test("a child header without Requirement: is a requirement; a deeper one isn't"):
        assertTrue(
          read(
            "## Requirements",
            "### Upcoming",
            "The scheduler SHALL list events.",
            "#### Note",
            "The note SHALL be ignored."
          ) == List(Statement(3, "The scheduler SHALL list events."))
        )
      ,
      test("the section ends at a header at its level"):
        assertTrue(
          read(
            "## Requirements",
            "### Requirement: Upcoming",
            "The scheduler SHALL list events.",
            "## Notes",
            "### Later",
            "The later section SHALL be ignored."
          ) == List(Statement(3, "The scheduler SHALL list events."))
        )
      ,
      test("with no text, the header's title, Requirement: prefix and all, at the header's line"):
        assertTrue(
          read(("## Requirements" :: "### The scheduler SHALL list events." :: scenario)*) ==
            List(Statement(2, "The scheduler SHALL list events.")),
          read(("## Requirements" :: "### Requirement: Upcoming ##" :: scenario)*) ==
            List(Statement(2, "Requirement: Upcoming"))
        )
      ,
      test("no Requirements section: nothing"):
        assertTrue(read("# Notes", "The scheduler SHALL list events.").isEmpty)
    ),
    suite("a delta spec")(
      test("the ADDED and MODIFIED requirements, in document order; REMOVED and RENAMED not"):
        val delta = List(
          "## ADDED Requirements",
          "",
          "### Requirement: Upcoming",
          "The scheduler SHALL list upcoming events.",
          "",
          "## REMOVED Requirements",
          "",
          "### Requirement: Reminders",
          "**Reason**: Replaced by notifications SHALL.",
          "",
          "## RENAMED Requirements",
          "",
          "- FROM: `### Requirement: Sync`",
          "- TO: `### Requirement: Calendar sync`",
          "",
          "## MODIFIED Requirements",
          "",
          "### Requirement: Past",
          "The scheduler SHALL hide past events."
        )
        assertTrue(
          read(delta*) == List(
            Statement(4, "The scheduler SHALL list upcoming events."),
            Statement(19, "The scheduler SHALL hide past events.")
          )
        )
      ,
      test("section titles in any case; a requirement header with no space after ###"):
        assertTrue(
          read("## Added Requirements", "###Requirement: Upcoming", "The scheduler SHALL list.") ==
            List(Statement(3, "The scheduler SHALL list."))
        )
      ,
      test("only Requirement: headers are requirements in a delta section"):
        assertTrue(
          read(
            "## ADDED Requirements",
            "### Upcoming",
            "The scheduler SHALL list events.",
            "### Requirement: Past",
            "The scheduler SHALL hide past events."
          ) == List(Statement(5, "The scheduler SHALL hide past events."))
        )
      ,
      test("with no text, the header's title, Requirement: prefix and all"):
        assertTrue(
          read(
            ("## ADDED Requirements" :: "### Requirement: The scheduler SHALL list" :: scenario)*
          ) ==
            List(Statement(2, "Requirement: The scheduler SHALL list"))
        )
    ),
    suite("a requirement's text")(
      test("its lines trimmed and joined with line breaks, up to the next header"):
        assertTrue(
          read(
            ("## Requirements" :: "### Requirement: Upcoming" :: "  The scheduler SHALL list  " ::
              "" :: "upcoming events." :: scenario)*
          ) == List(Statement(3, "The scheduler SHALL list\nupcoming events."))
        )
      ,
      test("fenced lines skipped, backticks or tildes, a header inside one included"):
        assertTrue(
          read(
            "## Requirements",
            "### Requirement: Upcoming",
            "The scheduler SHALL list events.",
            "````",
            "### Requirement: Fenced",
            "```",
            "````",
            "~~~",
            "text",
            "~~~",
            "in order."
          ) == List(Statement(3, "The scheduler SHALL list events.\nin order."))
        )
      ,
      test("a backtick line with a backtick after it is inline code, not a fence"):
        assertTrue(
          read(
            "## Requirements",
            "### Requirement: Upcoming",
            "```npm test``` SHALL pass.",
            "### Requirement: Past",
            "The scheduler SHALL hide past events."
          ) == List(
            Statement(3, "```npm test``` SHALL pass."),
            Statement(5, "The scheduler SHALL hide past events.")
          )
        )
      ,
      test("label lines dropped, unless they're all there is"):
        assertTrue(
          read(
            "## Requirements",
            "### Requirement: Upcoming",
            "**ID**: SCHED-1",
            "The scheduler SHALL list events.",
            "### Requirement: Constraint",
            "**Constraint**: The scheduler SHALL list events.",
            "**Priority**: high"
          ) == List(
            Statement(4, "The scheduler SHALL list events."),
            Statement(6, "**Constraint**: The scheduler SHALL list events.\n**Priority**: high")
          )
        )
      ,
      test("whitespace is JavaScript's, as OpenSpec's parser reads it"):
        // Written as code points: no-break space, ideographic space, and the byte-order mark,
        // which JavaScript's \s and trim() take as whitespace and Java's \s doesn't.
        val nbsp = 0xa0.toChar.toString
        val ideographic = 0x3000.toChar.toString
        val bom = 0xfeff.toChar.toString
        assertTrue(
          read(
            s"##${nbsp}ADDED Requirements",
            s"###${nbsp}Requirement: Upcoming",
            s"${nbsp}The scheduler SHALL list events.$bom",
            nbsp * 3,
            s"$ideographic**ID**: SCHED-1"
          ) == List(Statement(3, "The scheduler SHALL list events."))
        )
      ,
      test("a fence closes only with its own character; a title's C# keeps its sign"):
        val emSpace = 0x2003.toChar.toString
        assertTrue(
          read(
            "## Requirements",
            s"###${emSpace}Requirement: Written in C#",
            "```",
            "~~~",
            "### Requirement: Fenced",
            "```",
            "#### Scenario: One"
          ) == List(Statement(2, "Requirement: Written in C#"))
        )
      ,
      test("a header whose title holds a line separator is no requirement, but ends the text"):
        // JavaScript's . doesn't match U+2028 or U+2029, so OpenSpec's header pattern fails there;
        // its test for where a requirement's text ends, `^#{1,6}\s`, doesn't need one.
        val separator = 0x2028.toChar.toString
        assertTrue(
          read(
            "## Requirements",
            "### Requirement: Upcoming",
            "The scheduler SHALL list events.",
            s"### Requirement: Past${separator}events",
            "The scheduler SHALL hide past events."
          ) == List(Statement(3, "The scheduler SHALL list events."))
        )
      ,
      test("delta section titles match in any case, whatever the locale"):
        assertTrue(
          read("## modified REQUIREMENTS", "### Requirement: Past", "The scheduler SHALL hide.") ==
            List(Statement(3, "The scheduler SHALL hide."))
        )
      ,
      test("many requirements: each text ends at its own next header"):
        val many = (1 to 2000).toList.flatMap(i =>
          List(s"### Requirement: R$i", s"The scheduler SHALL list event $i.")
        )
        val found = read(("## Requirements" :: many)*)
        assertTrue(
          found.size == 2000,
          found.lastOption.contains(Statement(4001, "The scheduler SHALL list event 2000."))
        )
      ,
      test("Windows line endings and a byte-order mark are no part of it"):
        val bom = 0xfeff.toChar.toString
        assertTrue(
          OpenSpecMarkdown.statements(
            s"$bom## Requirements\r\n### Requirement: Upcoming\r\nThe scheduler SHALL list.\r\n"
          ) == List(Statement(3, "The scheduler SHALL list."))
        )
    )
  )
