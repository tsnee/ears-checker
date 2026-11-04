package io.github.tsnee.ears.cli

import java.time.Instant

import io.github.tsnee.ears.Profile
import zio.LogLevel
import zio.test.*

/** The pure parts of logging: which levels a run shows, and how an entry is written. */
object LoggingSuite extends ZIOSpecDefault:

  private val noon = Instant.parse("2026-11-04T12:00:00Z")

  // ZIO's LogLevel is a case class without a CanEqual instance.
  private given CanEqual[LogLevel, LogLevel] = CanEqual.derived

  def spec = suite("Logging")(
    suite("minimum level")(
      test("warnings and errors by default"):
        assertTrue(Logging.minimumLevel(verbose = false) == LogLevel.Warning)
      ,
      test("every level from debug with --verbose"):
        assertTrue(Logging.minimumLevel(verbose = true) == LogLevel.Debug)
    ),
    suite("line")(
      test("timestamp, level, and message"):
        assertTrue(
          Logging.line(noon, LogLevel.Info, "Finished", Map.empty) ==
            "2026-11-04T12:00:00Z INFO Finished"
        )
      ,
      test("details follow as key=value pairs, sorted by key"):
        assertTrue(
          Logging
            .line(noon, LogLevel.Error, "Cannot read input", Map("file" -> "a", "exit" -> "2")) ==
            "2026-11-04T12:00:00Z ERROR Cannot read input exit=2 file=a"
        )
      ,
      test("line breaks in the message and in a value are written as \\n"):
        assertTrue(
          Logging.line(noon, LogLevel.Error, "two\nlines", Map("reason" -> "a\nb")) ==
            "2026-11-04T12:00:00Z ERROR two\\nlines reason=a\\nb"
        )
      ,
      test("a value with a space, an equals sign, or a quote is quoted"):
        assertTrue(
          Logging.line(
            noon,
            LogLevel.Debug,
            "Arguments",
            Map("a" -> "two words", "b" -> "k=v", "c" -> "say \"hi\"", "d" -> "")
          ) ==
            """2026-11-04T12:00:00Z DEBUG Arguments a="two words" b="k=v" c="say \"hi\"" d="""""
        )
      ,
      test("a backslash is escaped, so an escaped line break can't be forged"):
        assertTrue(
          Logging.line(noon, LogLevel.Info, "Read input", Map("file" -> "a\\nb")) ==
            "2026-11-04T12:00:00Z INFO Read input file=a\\\\nb"
        )
      ,
      test("whatever the details hold, an entry is one line"):
        check(Gen.string, Gen.string) { (message, value) =>
          val written = Logging.line(noon, LogLevel.Error, message, Map("key" -> value))
          assertTrue(!written.contains('\n'), !written.contains('\r'))
        }
    ),
    suite("input details")(
      test("a file: its name, format, profile, and output"):
        assertTrue(
          Logging.inputDetails(Settings(Some("notes.md"), None, None, None)) ==
            Map(
              "file" -> "notes.md",
              "format" -> "markdown",
              "profile" -> "classic",
              "output" -> "text"
            )
        )
      ,
      test("standard input, with the options given"):
        assertTrue(
          Logging.inputDetails(
            Settings(
              None,
              Some(InputFormat.OpenSpecJson),
              Some(Profile.OpenSpec),
              Some(OutputFormat.Json)
            )
          ) ==
            Map(
              "file" -> "<stdin>",
              "format" -> "openspec",
              "profile" -> "openspec",
              "output" -> "json"
            )
        )
    )
  )
