package io.github.tsnee.ears.cli

import cats.data.NonEmptyList
import io.github.tsnee.ears.Profile
import io.github.tsnee.ears.cli.TerminalText.acted
import io.github.tsnee.ears.cli.TerminalText.controls
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

  /** Space, tab, line feed, vertical tab, form feed, and carriage return. */
  private def spacing(c: Char): Boolean = c == ' ' || (c >= '\t' && c <= '\r')

  /** How text output shows a character: a backslash, u, and four lowercase hexadecimal digits,
    * built here without a format string.
    */
  private def hex(c: Char): String =
    "\\u" + Integer.toHexString(c.toInt).reverse.padTo(4, '0').reverse

  /** How every JSON document starts: the format version, then the release that wrote it. */
  private val head = "{\"version\":1,\"checker\":\"" + CheckerBuild.version + "\","

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
      val outcome = Checker.outcome(
        plain,
        Right("The scheduler lists upcoming events.\nThe scheduler hides past events.\n")
      )
      assertTrue(
        lines(outcome.stdout).map(_.takeWhile(_ != ' ')) == List("<stdin>:1:", "<stdin>:2:")
      )
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
    test("selected profile: the OpenSpec profile on plain text reports wrong case and its fix"):
      val outcome = Checker.outcome(
        settings(None, None, Some(Profile.OpenSpec), None),
        Right("The scheduler shall list upcoming events.")
      )
      assertTrue(
        outcome.stdout.contains("wrong-case"),
        outcome.stdout.contains("The scheduler SHALL list upcoming events.")
      )
    ,
    test("text output, by default or selected: one human-readable line per finding"):
      val input = Right("The scheduler lists upcoming events.")
      val selected = Checker.outcome(settings(None, None, None, Some(OutputFormat.Text)), input)
      val default = Checker.outcome(plain, input)
      assertTrue(
        lines(selected.stdout).size == 1,
        !selected.stdout.trim.startsWith("{"),
        default == selected
      )
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
      assertTrue(
        outcome.exitCode == 2,
        outcome.stderr.startsWith("Not OpenSpec change or spec JSON:"),
        outcome.stdout.isEmpty
      )
    ,
    suite("characters a terminal acts on")(
      test("an escape sequence in a requirement shows as \\u001b in its fix"):
        val outcome = Checker.outcome(
          plain,
          Right("When an order arrives the scheduler shall list events \u001b[2K\u001b[1A.")
        )
        assertTrue(
          outcome.stdout.contains("list events \\u001b[2K\\u001b[1A.\""),
          !outcome.stdout.filterNot(_ == '\n').exists(acted)
        )
      ,
      test("a line separator in a requirement name keeps the finding on one line"):
        val spec =
          """{"id": "reminders", "requirements": [
            |  {"name": "Up\u2028coming", "text": "The scheduler lists upcoming events."}
            |]}""".stripMargin
        val outcome =
          Checker.outcome(settings(None, Some(InputFormat.OpenSpecJson), None, None), Right(spec))
        assertTrue(
          lines(outcome.stdout).size == 1,
          outcome.stdout.contains("reminders/\"Up\\u2028coming\""),
          !outcome.stdout.filterNot(_ == '\n').exists(acted)
        )
      ,
      test("a message from an unreadable input shows its control characters as \\uXXXX"):
        val outcome = Checker.outcome(plain, Left("Cannot read mis\u001b[1Asing.txt"))
        assertTrue(outcome.stderr == "Cannot read mis\\u001b[1Asing.txt\n")
      ,
      test("backslashes in a fix are left as they are"):
        val outcome = Checker.outcome(
          plain,
          Right("When a file arrives in C:\\orders\\new the importer shall queue it.")
        )
        assertTrue(outcome.stdout.contains("Fix: \"When a file arrives in C:\\orders\\new, the"))
      ,
      test("a vertical tab in a requirement shows as one space in its fix"):
        val outcome = Checker.outcome(
          plain,
          Right("When an order\u000barrives the scheduler shall list events.")
        )
        assertTrue(outcome.stdout.contains("Fix: \"When an order arrives, the scheduler"))
      ,
      test("a character inside a word of a fix shows as \\u and its four hexadecimal digits"):
        // Inside "events", which the fix carries through; whitespace would split the word instead.
        val sentence = "When an order arrives the scheduler shall list events."
        val word = sentence.indexOf("events")
        check(controls.filterNot(spacing), Gen.int(1, 5)) { (c, at) =>
          val position = word + at
          val text = sentence.take(position) + c.toString + sentence.drop(position)
          val outcome = Checker.outcome(plain, Right(text))
          // The fix adds a comma after "arrives"; the end of the sentence is as written.
          val shown = "shall list " + "events".take(at) + hex(c) + "events".drop(at) + ".\""
          assertTrue(
            outcome.stdout.contains(shown),
            !outcome.stdout.filterNot(_ == '\n').exists(acted)
          )
        }
      ,
      test("a character in a missing file's name shows as \\u and its four hexadecimal digits"):
        check(controls, Gen.int(0, 8)) { (c, at) =>
          val name = "missing.txt"
          val position = at.min(name.length)
          val message = "Cannot read " + name.take(position) + c.toString + name.drop(position)
          val outcome = Checker.outcome(plain, Left(message))
          val shown = "Cannot read " + name.take(position) + hex(c) + name.drop(position)
          assertTrue(outcome.stderr == shown + "\n")
        }
    ),
    suite("several inputs")(
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
    ),
    suite("the JSON document, whole (docs/json-output.md)")(
      test("no findings: the version, the checker, the count, and an empty list"):
        val outcome = Checker.outcome(
          settings(None, None, None, Some(OutputFormat.Json)),
          Right("The scheduler shall list upcoming events.")
        )
        assertTrue(outcome.stdout == head + "\"checked\":1,\"findings\":[]}\n")
      ,
      test("a finding on a line"):
        val outcome = Checker.outcome(
          file("reqs.txt", Some(OutputFormat.Json)),
          Right(
            "The scheduler shall list upcoming events.\nThe scheduler should list upcoming events.\n"
          )
        )
        assertTrue(
          outcome.stdout ==
            head + """"checked":2,"findings":[{"location":{"type":"line","file":"reqs.txt","line":2},""" +
            """"requirement":"The scheduler should list upcoming events.","kind":"weak-modal",""" +
            """"message":"Use shall instead of \"should\".","span":{"start":14,"end":20},""" +
            """"fix":"The scheduler shall list upcoming events."}]}""" + "\n"
        )
      ,
      test("a finding in a change requirement, without a fix"):
        val outcome = Checker.outcome(
          settings(None, Some(InputFormat.OpenSpecJson), None, Some(OutputFormat.Json)),
          Right(changeMissingModal)
        )
        assertTrue(
          outcome.stdout ==
            head + """"checked":1,"findings":[{"location":{"type":"change",""" +
            """"change":"add-reminders","capability":"reminders","requirement":"Reminder sent"},""" +
            """"requirement":"The scheduler sends a notice.","kind":"missing-modal",""" +
            """"message":"State the response with SHALL.","span":{"start":0,"end":28},"fix":null}]}""" +
            "\n"
        )
      ,
      test("a finding in a spec requirement, without a fix"):
        val spec =
          """{"id": "reminders", "requirements": [
            |  {"name": "Upcoming", "text": "The scheduler lists upcoming events."}
            |]}""".stripMargin
        val outcome = Checker.outcome(
          settings(None, Some(InputFormat.OpenSpecJson), None, Some(OutputFormat.Json)),
          Right(spec)
        )
        assertTrue(
          outcome.stdout ==
            head + """"checked":1,"findings":[{"location":{"type":"spec","spec":"reminders",""" +
            """"requirement":"Upcoming"},"requirement":"The scheduler lists upcoming events.",""" +
            """"kind":"missing-modal","message":"State the response with SHALL.",""" +
            """"span":{"start":0,"end":35},"fix":null}]}""" + "\n"
        )
      ,
      test("the checker is this build's version: a release, or the next one as a snapshot"):
        assertTrue(CheckerBuild.version.matches("""\d+\.\d+\.\d+(-SNAPSHOT)?"""))
      ,
      test("standard input: a line location named <stdin>"):
        val outcome = Checker.outcome(
          settings(None, None, None, Some(OutputFormat.Json)),
          Right("The scheduler lists upcoming events.")
        )
        assertTrue(
          outcome.stdout.contains(
            "\"location\":{\"type\":\"line\",\"file\":\"<stdin>\",\"line\":1}"
          )
        )
      ,
      test("several inputs: one document counting both, followed by one line break"):
        val outcome = combined(
          file("a.txt", Some(OutputFormat.Json)) -> "The scheduler shall list upcoming events.",
          file("b.txt", Some(OutputFormat.Json)) -> "The scheduler shall hide past events."
        )
        assertTrue(
          outcome.exitCode == 0,
          outcome.stdout == head + "\"checked\":2,\"findings\":[]}\n"
        )
      ,
      test("a span counts UTF-16 code units: an emoji counts as two"):
        val outcome = Checker.outcome(
          settings(None, None, None, Some(OutputFormat.Json)),
          Right("The 📅 scheduler should list upcoming events.")
        )
        assertTrue(outcome.stdout.contains("\"span\":{\"start\":17,\"end\":23}"))
      ,
      test("every document is one line that starts with the version and the checker"):
        val sentences = Gen.elements(
          "The scheduler shall list upcoming events.",
          "The scheduler should list upcoming events.",
          "The scheduler lists upcoming events.",
          "When an order arrives the scheduler shall list events.",
          "  ",
          "The scheduler shall list\u2028upcoming events."
        )
        check(Gen.listOfBounded(0, 4)(sentences)) { picked =>
          val outcome = Checker.outcome(
            settings(None, None, None, Some(OutputFormat.Json)),
            Right(picked.mkString("\n"))
          )
          assertTrue(
            outcome.stdout.startsWith(head + "\"checked\":"),
            outcome.stdout.endsWith("}\n"),
            outcome.stdout.count(_ == '\n') == 1
          )
        }
    )
  )
