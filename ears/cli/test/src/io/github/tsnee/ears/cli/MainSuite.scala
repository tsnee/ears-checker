package io.github.tsnee.ears.cli

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
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

  /** A closed stream: every read fails with the JDK's `IOException("Stream closed")`. */
  private val closedStream: UIO[InputStream] = ZIO.succeed {
    val stream = BufferedInputStream(ByteArrayInputStream(Array.emptyByteArray))
    stream.close()
    stream
  }

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
    test("several files: each format and default profile come from the file's own name"):
      for
        markdown <- tempFile(".md", "- The scheduler shall list upcoming events.\n")
        json <- tempFile(
          ".json",
          """{"id": "reminders", "requirements": [
            |  {"name": "Upcoming", "text": "The scheduler shall list upcoming events."}
            |]}""".stripMargin
        )
        outcome <- run(markdown.toString, json.toString)
      yield assertTrue(
        outcome.exitCode == 1,
        Predef.augmentString(outcome.stdout).linesIterator.toList.map(_.takeWhile(_ != '/')) ==
          List("reminders"),
        outcome.stdout.contains("wrong-case")
      )
    ,
    test("two missing files: the message names only the first"):
      run("missing.txt", "absent.txt").map { outcome =>
        assertTrue(
          outcome.exitCode == 2,
          outcome.stderr.contains("missing.txt"),
          !outcome.stderr.contains("absent.txt")
        )
      }
    ,
    test("a failing file before a missing one: status 2 and nothing on standard output"):
      for
        path <- tempFile(".txt", "The scheduler lists upcoming events.\n")
        outcome <- run(path.toString, "missing.txt")
      yield assertTrue(outcome.exitCode == 2, outcome.stdout.isEmpty)
    ,
    test("a missing file before a failing one: status 2 and nothing on standard output"):
      for
        path <- tempFile(".txt", "The scheduler lists upcoming events.\n")
        outcome <- run("missing.txt", path.toString)
      yield assertTrue(
        outcome.exitCode == 2,
        outcome.stderr.contains("missing.txt"),
        outcome.stdout.isEmpty
      )
    ,
    test("an unparsable file before a missing one: the message is about the unparsable file"):
      for
        path <- tempFile(".json", "{not json")
        outcome <- run(path.toString, "absent.txt")
      yield assertTrue(
        outcome.exitCode == 2,
        outcome.stderr.nonEmpty,
        !outcome.stderr.contains("absent.txt")
      )
    ,
    test("--help: exit status 0 and the usage text on standard output"):
      run("--help").map { outcome =>
        assertTrue(
          outcome.exitCode == 0,
          outcome.stdout.contains("Usage"),
          outcome.stderr.isEmpty
        )
      }
    ,
    suite("the console wiring")(
      test("a passing requirement: exit code 0 and nothing written"):
        for
          code <- Main.cli(Nil, ZIO.succeed("The scheduler shall list upcoming events.\n"))
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(code.code == 0, out.mkString.isEmpty, err.mkString.isEmpty)
      ,
      test("a failing requirement: exit code 1 and the finding on standard output"):
        for
          code <- Main.cli(Nil, ZIO.succeed("The scheduler lists events.\n"))
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(
          code.code == 1,
          out.mkString.contains("missing-modal"),
          err.mkString.isEmpty
        )
      ,
      test("--help: exit code 0 and the usage text on standard output"):
        for
          code <- Main.cli(List("--help"), noStdin)
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(code.code == 0, out.mkString.contains("Usage"), err.mkString.isEmpty)
      ,
      test("bad arguments: exit code 2 and the usage text on standard error"):
        for
          code <- Main.cli(List("--format", "yaml"), noStdin)
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(code.code == 2, out.mkString.isEmpty, err.mkString.contains("Usage"))
    ),
    suite("unreadable input")(
      test("standard input that fails: status 2 and the cause on standard error"):
        for
          stream <- closedStream
          outcome <- Main.program(Nil, Main.readAll(stream))
        yield assertTrue(
          outcome.exitCode == 2,
          outcome.stdout.isEmpty,
          outcome.stderr.contains("Cannot read standard input: Stream closed")
        )
      ,
      test("an error with no message: the error's class names the cause"):
        Main.program(Nil, ZIO.fail(IllegalStateException())).map { outcome =>
          assertTrue(
            outcome.exitCode == 2,
            outcome.stderr.contains(
              "Cannot read standard input: java.lang.IllegalStateException"
            )
          )
        }
      ,
      test("a directory given as a file: status 2, its name, and the system's reason"):
        for
          directory <- ZIO.attemptBlocking {
            val path = Files.createTempDirectory("ears-check")
            path.toFile.deleteOnExit()
            path
          }
          outcome <- run(directory.toString)
          prefix = s"Cannot read $directory: "
          reason = outcome.stderr.stripPrefix(prefix).trim
        yield assertTrue(
          outcome.exitCode == 2,
          outcome.stdout.isEmpty,
          outcome.stderr.startsWith(prefix),
          reason.nonEmpty,
          reason != "no such file"
        )
      ,
      test("readAll decodes a stream as UTF-8"):
        val text = "The scheduler shall list upcoming events — daily.\n"
        Main
          .readAll(ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
          .map(read => assertTrue(read == text))
    )
  )
