package io.github.tsnee.ears.cli

import java.time.Instant
import scala.collection.mutable.ListBuffer

import zio.*
import zio.test.*

/** The shared program over a fake `Reading`, and the read-failure messages: the same on every
  * platform, so these run on the JVM and on Node.
  */
object CliSuite extends ZIOSpecDefault:

  private val passing = "The scheduler shall list upcoming events.\n"
  private val failing = "The scheduler lists events.\n"

  /** A `Reading` over named files; any other name can't be read. Standard input isn't expected. */
  private def reading(files: Map[String, IO[(ReadFailure, String), String]]): Reading =
    Reading(
      name => files.getOrElse(name, unreadable("ENOENT")),
      unreadable("standard input not expected")
    )

  private def unreadable(error: String): IO[(ReadFailure, String), String] =
    ZIO.fail(ReadFailure.Unreadable -> error)

  private def run(files: Map[String, IO[(ReadFailure, String), String]], arguments: String*) =
    Cli.program(arguments.toList, reading(files))

  def spec = suite("ears-check, shared")(
    suite("read-failure messages")(
      test("an input that can't be read: only its name, the same on every platform"):
        assertTrue(ReadFailure.Unreadable.describe("notes.md") == "Cannot read notes.md")
      ,
      test("an input that isn't UTF-8: where"):
        assertTrue(
          ReadFailure.NotUtf8(2, 7).describe("notes.md") ==
            "Cannot read notes.md: not valid UTF-8 at line 2, column 7"
        )
    ),
    suite("program")(
      test("a file is read through the seam"):
        run(Map("notes.txt" -> ZIO.succeed(failing)), "notes.txt").map(outcome =>
          assertTrue(outcome.exitCode == 1, outcome.stdout.startsWith("notes.txt:1: missing-modal"))
        )
      ,
      test("standard input is read when no file is given"):
        Cli
          .program(Nil, Reading(_ => unreadable("ENOENT"), ZIO.succeed(passing)))
          .map(outcome => assertTrue(outcome.exitCode == 0, outcome.stdout.isEmpty))
      ,
      test("a file that can't be read: status 2 and its name"):
        run(Map.empty, "missing.txt").map(outcome =>
          assertTrue(
            outcome.exitCode == 2,
            outcome.stdout.isEmpty,
            outcome.stderr == "Cannot read missing.txt\n"
          )
        )
      ,
      test("a file that isn't UTF-8: status 2, its name, and where"):
        run(Map("notes.txt" -> ZIO.fail(ReadFailure.NotUtf8(3, 1) -> "invalid UTF-8")), "notes.txt")
          .map(outcome =>
            assertTrue(
              outcome.exitCode == 2,
              outcome.stderr == "Cannot read notes.txt: not valid UTF-8 at line 3, column 1\n"
            )
          )
      ,
      test("standard input that fails: status 2, naming standard input"):
        Cli
          .program(Nil, Reading(_ => unreadable("ENOENT"), unreadable("Stream closed")))
          .map(outcome =>
            assertTrue(outcome.exitCode == 2, outcome.stderr == "Cannot read standard input\n")
          )
      ,
      test("several files are read in order, up to the first that fails"):
        for
          names <- Ref.make(List.empty[String])
          logged = Reading(
            name =>
              names.update(_ :+ name) *>
                (if name == "absent.txt" then unreadable("ENOENT") else ZIO.succeed(passing)),
            unreadable("standard input not expected")
          )
          outcome <- Cli.program(List("a.txt", "absent.txt", "c.txt"), logged)
          read <- names.get
        yield assertTrue(
          outcome.exitCode == 2,
          outcome.stderr == "Cannot read absent.txt\n",
          read == List("a.txt", "absent.txt")
        )
    ),
    suite("the application")(
      test("run takes its arguments from the command line"):
        Cli
          .run(reading(Map.empty))
          .provide(ZLayer.succeed(ZIOAppArgs(Chunk("--help"))))
          .map(code => assertTrue(code.code == 0))
      ,
      test("bootstrap installs its logger"):
        ZIO
          .scoped((ZLayer.succeed(ZIOAppArgs(Chunk.empty)) >>> Cli.bootstrap).build)
          .as(assertCompletes)
    ) @@ TestAspect.silent,
    suite("console")(
      test("a failing file: exit code 1 and the finding on standard output"):
        for
          code <- Cli.cli(List("notes.txt"), reading(Map("notes.txt" -> ZIO.succeed(failing))))
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(
          code.code == 1,
          out.mkString.contains("missing-modal"),
          err.mkString.isEmpty
        )
      ,
      test("a missing file: exit code 2 and the message on standard error"):
        for
          code <- Cli.cli(List("missing.txt"), reading(Map.empty))
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(
          code.code == 2,
          out.mkString.isEmpty,
          err.mkString == "Cannot read missing.txt\n"
        )
    ) @@ TestAspect.silent,
    suite("logger")(
      test("each entry is stamped with the time it's given"):
        val noon = Instant.parse("2026-11-04T12:00:00Z")
        val lines = ListBuffer.empty[String]
        ZIO
          .withLogger(Cli.logger(LogLevel.Warning, line => lines += line, () => noon))(
            ZIO.logInfo("Hidden") *> ZIO.logError("Shown")
          )
          .as(assertTrue(lines.toList == List("2026-11-04T12:00:00Z ERROR Shown")))
      ,
      test("--verbose shows every level from debug"):
        val noon = Instant.parse("2026-11-04T12:00:00Z")
        val lines = ListBuffer.empty[String]
        ZIO
          .withLogger(Cli.loggerFor(List("--verbose"), line => lines += line, () => noon))(
            ZIO.logDebug("Starting")
          )
          .as(assertTrue(lines.toList == List("2026-11-04T12:00:00Z DEBUG Starting")))
    ) @@ TestAspect.silent
  )
