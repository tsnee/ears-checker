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
    // What's read from Markdown, and on which line, is the facade's: ears/markdown's conformance
    // suite runs those cases on every platform. Left here is what the CLI adds: the file name.
    suite("Markdown")(
      test("each sentence the facade reads becomes a candidate in the file, on its line"):
        val content =
          "# Reminders\n\nIntro.\n\n- The scheduler SHALL list events.\n- The calendar " +
            "SHALL sync.\n"
        assertTrue(
          Candidate.fromMarkdown("reqs.md", content) == Right(
            List(
              Candidate(Location.Line("reqs.md", 5), "The scheduler SHALL list events."),
              Candidate(Location.Line("reqs.md", 6), "The calendar SHALL sync.")
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
