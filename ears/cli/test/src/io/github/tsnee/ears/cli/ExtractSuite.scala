package io.github.tsnee.ears.cli

import zio.test.*

object ExtractSuite extends ZIOSpecDefault:

  private val changeJson =
    """{"id": "add-reminders", "title": "add-reminders", "deltaCount": 2, "deltas": [
      |  {"spec": "reminders", "operation": "ADDED", "description": "Add requirement",
      |   "requirement": {"name": "Reminder sent",
      |                   "text": "When a reminder is due, the scheduler SHALL send a notice.",
      |                   "scenarios": []}},
      |  {"spec": "reminders", "operation": "REMOVED", "description": "Remove requirement",
      |   "requirement": {"name": "Old reminder"}}
      |]}""".stripMargin

  private val specJson =
    """{"id": "reminders", "title": "reminders", "requirementCount": 2, "requirements": [
      |  {"name": "Upcoming", "text": "The scheduler SHALL list upcoming events.", "scenarios": []},
      |  {"name": "Past", "text": "The scheduler SHALL hide past events.", "scenarios": []}
      |]}""".stripMargin

  def spec = suite("Requirement extraction")(
    suite("plain text")(
      test("each nonblank line is one requirement, located by line"):
        val content =
          "The scheduler SHALL list upcoming events.\n\n  The scheduler SHALL hide past events.\n"
        assertTrue(
          Candidate.fromPlainText("reqs.txt", content) == List(
            Candidate(Location.Line("reqs.txt", 1), "The scheduler SHALL list upcoming events."),
            Candidate(Location.Line("reqs.txt", 3), "The scheduler SHALL hide past events.")
          )
        )
      ,
      test("a line of only Unicode whitespace is blank, not a requirement"):
        val content = "The scheduler SHALL list upcoming events.\n　\n"
        assertTrue(
          Candidate.fromPlainText("reqs.txt", content) == List(
            Candidate(Location.Line("reqs.txt", 1), "The scheduler SHALL list upcoming events.")
          )
        )
    ),
    suite("Markdown")(
      test("a list item is checked; a heading and a fenced code block are not"):
        val content =
          "# Reminders\n\n- The scheduler SHALL list upcoming events.\n\n```\nSHALL inside code\n```\n"
        assertTrue(
          Candidate.fromMarkdown("reqs.md", content) == List(
            Candidate(Location.Line("reqs.md", 3), "The scheduler SHALL list upcoming events.")
          )
        )
      ,
      test("a sentence that starts with inline code is located on its own line"):
        val content =
          "# Reminders\n\nIntro paragraph.\n\nMore intro text.\n\n- `ears-checker` shall check every line.\n"
        assertTrue(
          Candidate.fromMarkdown("reqs.md", content) == List(
            Candidate(Location.Line("reqs.md", 7), "ears-checker shall check every line.")
          )
        )
      ,
      test("escapes and entities before a sentence don't move it to another line"):
        val content =
          "Some \\*escaped\\* text &amp;amp; more.\nThe scheduler shall list events.\n"
        assertTrue(
          Candidate.fromMarkdown("reqs.md", content) == List(
            Candidate(Location.Line("reqs.md", 2), "The scheduler shall list events.")
          )
        )
      ,
      test("a sentence whose keyword is only inside inline code is not checked"):
        assertTrue(Candidate.fromMarkdown("reqs.md", "Write `SHALL` in uppercase.\n").isEmpty)
      ,
      test("sentences split at a period and whitespace; soft line breaks become spaces"):
        val content =
          "Reminders are useful. When a reminder is due,\nthe scheduler SHALL send a notice.\n"
        assertTrue(
          Candidate.fromMarkdown("reqs.md", content) == List(
            Candidate(
              Location.Line("reqs.md", 1),
              "When a reminder is due, the scheduler SHALL send a notice."
            )
          )
        )
    ),
    suite("OpenSpec JSON")(
      test("a change: added and modified requirements are checked, removed ones are not"):
        assertTrue(
          Candidate.fromOpenSpec(changeJson) == Right(
            List(
              Candidate(
                Location.ChangeRequirement("add-reminders", "reminders", "Reminder sent"),
                "When a reminder is due, the scheduler SHALL send a notice."
              )
            )
          )
        )
      ,
      test("a spec: every requirement is checked"):
        assertTrue(
          Candidate.fromOpenSpec(specJson).map(_.map(_.location)) == Right(
            List(
              Location.SpecRequirement("reminders", "Upcoming"),
              Location.SpecRequirement("reminders", "Past")
            )
          )
        )
      ,
      test("malformed JSON is an error"):
        assertTrue(Candidate.fromOpenSpec("{not json").isLeft)
    )
  )
