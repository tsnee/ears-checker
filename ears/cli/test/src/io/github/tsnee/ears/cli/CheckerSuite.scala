package io.github.tsnee.ears.cli

import cats.data.NonEmptyList
import io.github.tsnee.ears.Profile
import zio.test.*

object CheckerSuite extends ZIOSpecDefault:

  private def settings(
      file: Option[String],
      format: Option[InputFormat],
      profile: Option[Profile],
      output: Option[OutputFormat]
  ): Settings = Settings(file, format, profile, output)

  private val plain = settings(None, None, None, None)

  private def file(name: String, output: Option[OutputFormat]): Settings =
    settings(Some(name), None, None, output)

  /** The outcome of checking several inputs, each given as its settings and its content. */
  private def combined(first: (Settings, String), rest: (Settings, String)*): Outcome =
    Checker.outcome(
      NonEmptyList(first, rest.toList).traverse { (settings, content) =>
        Checker.candidates(settings, content).map(settings -> _)
      }
    )

  private def lines(text: String): List[String] = text.linesIterator.filter(_.nonEmpty).toList

  private val changeMissingModal =
    """{"id": "add-reminders", "deltas": [
      |  {"spec": "reminders", "operation": "ADDED",
      |   "requirement": {"name": "Reminder sent", "text": "The scheduler sends a notice."}}
      |]}""".stripMargin

  private val cleanSpec =
    """{"id": "reminders", "requirements": [
      |  {"name": "Upcoming", "text": "The scheduler SHALL list upcoming events."}
      |]}""".stripMargin

  /** A requirement wrapped onto two lines, as the JSON from a hand-wrapped spec can contain. */
  private val wrappedSpec =
    """{"id": "reminders", "requirements": [
      |  {"name": "Upcoming", "text": "The scheduler shall list upcoming\nevents in order."}
      |]}""".stripMargin

  def spec = suite("EARS checker")(
    test("standard input: each line is checked and located as <stdin>"):
      val outcome = Checker.outcome(plain, Right("The scheduler lists upcoming events.\n"))
      assertTrue(outcome.stdout.startsWith("<stdin>:1:"))
    ,
    test("finding contents: change, capability, requirement, and diagnostic"):
      val outcome = Checker.outcome(
        settings(None, Some(InputFormat.OpenSpecJson), None, None),
        Right(changeMissingModal)
      )
      assertTrue(
        outcome.stdout.startsWith("add-reminders/reminders/\"Reminder sent\": missing-modal:")
      )
    ,
    test("a fix is reported with its corrected sentence"):
      val outcome = Checker.outcome(
        settings(None, None, Some(Profile.OpenSpec), None),
        Right("The scheduler shall list upcoming events.")
      )
      assertTrue(outcome.stdout.contains("The scheduler SHALL list upcoming events."))
    ,
    test("text output: one line per finding"):
      val outcome = Checker.outcome(
        settings(None, None, None, Some(OutputFormat.Text)),
        Right("The scheduler lists upcoming events.")
      )
      assertTrue(lines(outcome.stdout).size == 1)
    ,
    test("default output is text: one human-readable line"):
      val outcome = Checker.outcome(plain, Right("The scheduler lists upcoming events."))
      assertTrue(lines(outcome.stdout).size == 1, !outcome.stdout.trim.startsWith("{"))
    ,
    test("JSON output with no findings: one document with an empty list"):
      val outcome = Checker.outcome(
        settings(None, None, None, Some(OutputFormat.Json)),
        Right("The scheduler shall list upcoming events.")
      )
      assertTrue(
        lines(outcome.stdout).size == 1,
        outcome.stdout.contains("\"findings\":[]"),
        outcome.stdout.contains("\"checked\":1")
      )
    ,
    test("JSON output with a finding names its kind and fix"):
      val outcome = Checker.outcome(
        settings(None, None, Some(Profile.OpenSpec), Some(OutputFormat.Json)),
        Right("The scheduler shall list upcoming events.")
      )
      assertTrue(
        outcome.stdout.contains("\"kind\":\"wrong-case\""),
        outcome.stdout.contains("\"fix\":\"The scheduler SHALL list upcoming events.\"")
      )
    ,
    test("selected profile: the OpenSpec profile on plain text reports wrong case"):
      val outcome = Checker.outcome(
        settings(None, None, Some(Profile.OpenSpec), None),
        Right("The scheduler shall list upcoming events.")
      )
      assertTrue(outcome.stdout.contains("wrong-case"))
    ,
    test("default profile for OpenSpec input: the uppercase keyword passes"):
      val outcome =
        Checker
          .outcome(settings(None, Some(InputFormat.OpenSpecJson), None, None), Right(cleanSpec))
      assertTrue(outcome.exitCode == 0, outcome.stdout.isEmpty)
    ,
    test("default profile for plain text: the lowercase keyword passes"):
      val outcome = Checker.outcome(plain, Right("The scheduler shall list upcoming events."))
      assertTrue(outcome.exitCode == 0, outcome.stdout.isEmpty)
    ,
    test("text output: a requirement with a line break still gives one line per finding"):
      val outcome = Checker.outcome(
        settings(None, Some(InputFormat.OpenSpecJson), None, None),
        Right(wrappedSpec)
      )
      assertTrue(
        lines(outcome.stdout).size == 1,
        outcome.stdout.contains("Fix: \"The scheduler SHALL list upcoming events in order.\"")
      )
    ,
    test("JSON output keeps the exact corrected text, line break included"):
      val outcome = Checker.outcome(
        settings(None, Some(InputFormat.OpenSpecJson), None, Some(OutputFormat.Json)),
        Right(wrappedSpec)
      )
      assertTrue(
        outcome.stdout.contains("\"fix\":\"The scheduler SHALL list upcoming\\nevents in order.\"")
      )
    ,
    test("exit status 1 when a requirement fails"):
      assertTrue(
        Checker.outcome(plain, Right("The scheduler lists upcoming events.")).exitCode == 1
      )
    ,
    test("exit status 2 and a message on standard error for malformed JSON"):
      val outcome =
        Checker
          .outcome(settings(None, Some(InputFormat.OpenSpecJson), None, None), Right("{not json"))
      assertTrue(outcome.exitCode == 2, outcome.stderr.nonEmpty, outcome.stdout.isEmpty)
    ,
    test("exit status 2 and the read error on standard error for unreadable input"):
      val outcome = Checker.outcome(
        settings(Some("missing.txt"), None, None, None),
        Left("Cannot read missing.txt: no such file")
      )
      assertTrue(outcome.exitCode == 2, outcome.stderr.contains("missing.txt"))
    ,
    suite("several inputs")(
      test("every input is checked: JSON counts the requirements of both"):
        val outcome = combined(
          file("a.txt", Some(OutputFormat.Json)) -> "The scheduler shall list upcoming events.",
          file("b.txt", Some(OutputFormat.Json)) -> "The scheduler shall hide past events."
        )
        assertTrue(
          outcome.exitCode == 0,
          lines(outcome.stdout).size == 1,
          outcome.stdout.contains("\"checked\":2")
        )
      ,
      test("findings follow input order, not file names"):
        val outcome = combined(
          file("b.txt", None) -> "The scheduler lists upcoming events.",
          file("a.txt", None) -> "The scheduler hides past events."
        )
        assertTrue(
          outcome.exitCode == 1,
          lines(outcome.stdout).map(_.takeWhile(_ != ':')) == List("b.txt", "a.txt")
        )
      ,
      test("each input is classified under its own default profile"):
        val outcome = combined(
          file("notes.txt", None) -> "The scheduler shall list upcoming events.",
          file("reminders.json", None) -> cleanSpec
        )
        assertTrue(outcome.exitCode == 0, outcome.stdout.isEmpty)
      ,
      test("an input that can't be read or parsed: status 2, its cause, and no findings"):
        val outcome = Checker.outcome(Left("Cannot read missing.txt: no such file"))
        assertTrue(
          outcome.exitCode == 2,
          outcome.stderr.contains("missing.txt"),
          outcome.stdout.isEmpty
        )
      ,
      test("candidates reports malformed JSON as an error"):
        assertTrue(Checker.candidates(file("change.json", None), "{not json").isLeft)
    )
  )
