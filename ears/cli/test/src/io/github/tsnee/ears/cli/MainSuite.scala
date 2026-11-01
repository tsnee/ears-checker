package io.github.tsnee.ears.cli

import java.nio.file.Files
import java.nio.file.Path

import zio.*
import zio.test.*

/** The program end to end: reading a real file or standard input, then the outcome. */
object MainSuite extends ZIOSpecDefault:

  private def tempFile(suffix: String, content: String): Task[Path] =
    ZIO.attemptBlocking {
      val path = Files.createTempFile("ears-check", suffix)
      Files.writeString(path, content)
      path.toFile.deleteOnExit()
      path
    }

  private val noStdin: Task[String] = ZIO.fail(new IllegalStateException("stdin not expected"))

  private def run(arguments: String*): UIO[Outcome] = Main.program(arguments.toList, noStdin)

  def spec = suite("ears-check program")(
    test("plain-text file: a failing line exits 1 and names the file"):
      for
        path <- tempFile(
          ".txt",
          "The scheduler shall list upcoming events.\nThe scheduler lists events.\n"
        )
        outcome <- run(path.toString)
      yield assertTrue(
        outcome.exitCode == 1,
        outcome.stdout.startsWith(s"$path:2: missing-modal"),
        Predef.augmentString(outcome.stdout).linesIterator.size == 1
      )
    ,
    test("Markdown file: the format is inferred and code is skipped"):
      for
        path <- tempFile(
          ".md",
          "# Reminders\n\n- The scheduler shall list upcoming events.\n\n```\nSHALL\n```\n"
        )
        outcome <- run(path.toString)
      yield assertTrue(outcome.exitCode == 0, outcome.stdout.isEmpty)
    ,
    test("OpenSpec JSON file: the OpenSpec profile is the default"):
      for
        path <- tempFile(
          ".json",
          """{"id": "reminders", "requirements": [
            |  {"name": "Upcoming", "text": "The scheduler shall list upcoming events."}
            |]}""".stripMargin
        )
        outcome <- run(path.toString, "--output", "json")
      yield assertTrue(outcome.exitCode == 1, outcome.stdout.contains("\"kind\":\"wrong-case\""))
    ,
    test("piped input: standard input is read when no file is given"):
      Main
        .program(Nil, ZIO.succeed("The scheduler shall list upcoming events.\n"))
        .map(outcome => assertTrue(outcome.exitCode == 0, outcome.stdout.isEmpty))
    ,
    test("missing file: exit status 2 and a message naming the file"):
      run("missing.txt").map { outcome =>
        assertTrue(outcome.exitCode == 2, outcome.stderr.contains("missing.txt"))
      }
    ,
    test("bad arguments: exit status 2 and the usage text"):
      run("--format", "yaml").map { outcome =>
        assertTrue(outcome.exitCode == 2, outcome.stderr.contains("Usage"))
      }
    ,
    test("--help: exit status 0 and the usage text on standard output"):
      run("--help").map { outcome =>
        assertTrue(
          outcome.exitCode == 0,
          outcome.stdout.contains("Usage"),
          outcome.stderr.isEmpty
        )
      }
  )
