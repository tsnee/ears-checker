package io.github.tsnee.ears.cli

import java.nio.charset.StandardCharsets
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

  /** A requirement with a finding in Markdown, which reads only sentences with a modal keyword. */
  private val failingMarkdown = "The scheduler SHALL list upcoming events.\n"

  private def utf8(text: String): Array[Byte] = text.getBytes(StandardCharsets.UTF_8)

  /** A `Reading` of files and standard input where nothing is a directory. */
  private def readingOf(
      file: (String, Int) => IO[String, Array[Byte]],
      standardInput: Int => IO[String, Array[Byte]]
  ): Reading =
    Reading(file, standardInput, _ => ZIO.succeed(false), _ => ZIO.fail("ENOTDIR"))

  /** A `Reading` over named files' text, read as UTF-8; any other name can't be read. Standard
    * input isn't expected.
    */
  private def reading(files: Map[String, IO[String, String]]): Reading =
    readingOf(
      (name, _) => files.getOrElse(name, ZIO.fail("ENOENT")).map(utf8),
      _ => unreadable("standard input not expected")
    )

  private def unreadable(error: String): IO[String, Array[Byte]] = ZIO.fail(error)

  private def file(name: String) = Entry(name, EntryKind.File)

  private def directory(name: String) = Entry(name, EntryKind.Directory)

  /** The platform's error listing a directory the fake tree doesn't hold. */
  private val unlistable = "EACCES: permission denied"

  /** A `Reading` over a tree: each directory's entries by its path, and each file's text by its
    * path, read as UTF-8; anything else can't be read or listed. Standard input isn't expected.
    */
  private def tree(directories: Map[String, List[Entry]], texts: Map[String, String]): Reading =
    recordedTree(directories, texts, _ => ZIO.unit, _ => ZIO.unit)

  /** `tree`, giving each file read to `opened`, and the bytes each read asks for to `asked`. */
  private def recordedTree(
      directories: Map[String, List[Entry]],
      texts: Map[String, String],
      opened: String => UIO[Unit],
      asked: Int => UIO[Unit]
  ): Reading =
    Reading(
      (name, most) =>
        opened(name) *> asked(most) *>
          texts.get(name).fold(unreadable("ENOENT"))(text => ZIO.succeed(utf8(text))),
      _ => unreadable("standard input not expected"),
      name => ZIO.succeed(directories.contains(name)),
      name => directories.get(name).fold(ZIO.fail(unlistable))(ZIO.succeed(_))
    )

  /** The file each finding on standard output is located in, in order. */
  private def located(outcome: Outcome): List[String] =
    Predef.augmentString(outcome.stdout).linesIterator.toList.map(_.takeWhile(_ != ':'))

  private def run(files: Map[String, IO[String, String]], arguments: String*) =
    Cli.program(arguments.toList, reading(files))

  /** A `Reading` whose every input holds more bytes than are asked for: whatever the limit, over
    * it.
    */
  private val overLimit: Reading =
    readingOf(
      (_, most) => ZIO.succeed(Array.fill(most)('a'.toByte)),
      most => ZIO.succeed(Array.fill(most)('a'.toByte))
    )

  def spec = suite("ears-check, shared")(
    suite("read-failure messages")(
      test("an input that can't be read: only its name, the same on every platform"):
        assertTrue(ReadFailure.Unreadable.describe("notes.md") == "Cannot read notes.md")
      ,
      test("an input over the size limit: the limit, and how to raise it"):
        assertTrue(
          ReadFailure.TooLarge(1000).describe("notes.md") ==
            "Cannot read notes.md: more than 1000 bytes; --max-input-bytes raises the limit"
        )
      ,
      test("an input that isn't UTF-8: where"):
        assertTrue(
          ReadFailure.NotUtf8(2, 7).describe("notes.md") ==
            "Cannot read notes.md: not valid UTF-8 at line 2, column 7"
        )
      ,
      test("the log names each decoding failure"):
        assertTrue(
          ReadFailure.NotUtf8(1, 1).decodingError == "invalid UTF-8",
          ReadFailure.OtherEncoding("UTF-16").decodingError == "UTF-16 byte-order mark",
          ReadFailure.Unreadable.decodingError == "unreadable"
        )
      ,
      test("an input in another encoding: its name"):
        assertTrue(
          ReadFailure.OtherEncoding("UTF-16").describe("standard input") ==
            "Cannot read standard input: UTF-16, not UTF-8"
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
          .program(Nil, readingOf((_, _) => unreadable("ENOENT"), _ => ZIO.succeed(utf8(passing))))
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
      test("a file name with line feeds stays one line, showing \\u000a"):
        run(Map.empty, "x\nforged.md:1: missing-comma\ny.md").map(outcome =>
          assertTrue(
            outcome.stderr == "Cannot read x\\u000aforged.md:1: missing-comma\\u000ay.md\n"
          )
        )
      ,
      test("a bad argument with a line feed can't add a line of its own"):
        Cli
          .program(
            List("--format", "y\nforged"),
            readingOf((_, _) => unreadable("ENOENT"), _ => unreadable(""))
          )
          .map(outcome =>
            assertTrue(
              outcome.exitCode == 2,
              outcome.stderr.contains("y\\u000aforged"),
              !outcome.stderr.linesIterator.exists(_.startsWith("forged"))
            )
          )
      ,
      test("a bad argument with an escape sequence shows it as \\u001b"):
        Cli
          .program(
            List("--format", "y\u001b[2Kaml"),
            readingOf((_, _) => unreadable("ENOENT"), _ => unreadable(""))
          )
          .map(outcome =>
            assertTrue(
              outcome.exitCode == 2,
              outcome.stderr.contains("y\\u001b[2Kaml"),
              !outcome.stderr.exists(c => Character.isISOControl(c) && c != '\n')
            )
          )
      ,
      test("a file that isn't UTF-8: status 2, its name, and where"):
        // "a", "b", then 0xff, never valid in UTF-8, at line 3, column 1.
        val bytes = utf8("a\nb\n") ++ Array(0xff.toByte)
        Cli
          .program(List("notes.txt"), readingOf((_, _) => ZIO.succeed(bytes), _ => unreadable("")))
          .map(outcome =>
            assertTrue(
              outcome.exitCode == 2,
              outcome.stderr == "Cannot read notes.txt: not valid UTF-8 at line 3, column 1\n"
            )
          )
      ,
      test("a file in UTF-16: status 2, its name, and the encoding"):
        // UTF-16's little-endian byte-order mark, then "a".
        val bytes = Array(0xff, 0xfe, 'a'.toInt, 0).map(_.toByte)
        Cli
          .program(List("notes.txt"), readingOf((_, _) => ZIO.succeed(bytes), _ => unreadable("")))
          .map(outcome =>
            assertTrue(
              outcome.exitCode == 2,
              outcome.stderr == "Cannot read notes.txt: UTF-16, not UTF-8\n"
            )
          )
      ,
      test("the limit from --max-input-bytes, or 10 MiB, reaches every read"):
        for
          limits <- Ref.make(List.empty[Int])
          recording = readingOf(
            (_, most) => limits.update(_ :+ most).as(utf8(passing)),
            most => limits.update(_ :+ most).as(utf8(passing))
          )
          _ <- Cli.program(List("--max-input-bytes", "1000", "a.txt", "b.txt"), recording)
          _ <- Cli.program(Nil, recording)
          seen <- limits.get
        yield assertTrue(seen == List(1001, 1001, 10485761))
      ,
      test("an input over the limit: status 2, the message, and nothing on standard output"):
        Cli
          .program(List("--max-input-bytes", "1000", "notes.md"), overLimit)
          .map(outcome =>
            assertTrue(
              outcome.exitCode == 2,
              outcome.stdout.isEmpty,
              outcome.stderr ==
                "Cannot read notes.md: more than 1000 bytes; --max-input-bytes raises the limit\n"
            )
          )
      ,
      test("an input over the limit is logged with the limit"):
        val noon = Instant.parse("2026-11-04T12:00:00Z")
        val lines = ListBuffer.empty[String]
        ZIO
          .withLogger(Cli.logger(LogLevel.Warning, line => lines += line, () => noon))(
            Cli.program(List("--max-input-bytes", "1000", "notes.md"), overLimit)
          )
          .as(
            assertTrue(
              lines.toList.exists(line =>
                line.contains(" ERROR Cannot read input ") && line.contains(" limit=1000") &&
                  line.contains(" exit=2")
              )
            )
          )
      ,
      test("standard input that fails: status 2, naming standard input"):
        Cli
          .program(Nil, readingOf((_, _) => unreadable("ENOENT"), _ => unreadable("Stream closed")))
          .map(outcome =>
            assertTrue(outcome.exitCode == 2, outcome.stderr == "Cannot read standard input\n")
          )
      ,
      test("several files are read in order, up to the first that fails"):
        for
          names <- Ref.make(List.empty[String])
          logged = readingOf(
            (name, _) =>
              names.update(_ :+ name) *>
                (if name == "absent.txt" then unreadable("ENOENT") else ZIO.succeed(utf8(passing))),
            _ => unreadable("standard input not expected")
          )
          outcome <- Cli.program(List("a.txt", "absent.txt", "c.txt"), logged)
          read <- names.get
        yield assertTrue(
          outcome.exitCode == 2,
          outcome.stderr == "Cannot read absent.txt\n",
          read == List("a.txt", "absent.txt")
        )
      ,
      test("a failing file before a missing one: status 2 and nothing on standard output"):
        run(Map("notes.txt" -> ZIO.succeed(failing)), "notes.txt", "missing.txt").map(outcome =>
          assertTrue(outcome.exitCode == 2, outcome.stdout.isEmpty)
        )
    ),
    suite("directories")(
      test("the files under a directory, in name order, a subdirectory's where its name sorts"):
        val docs = Map(
          "docs" -> List(file("b.md"), file("a.md"), file("B.md"), directory("b")),
          "docs/b" -> List(file("c.md"))
        )
        val texts = List("docs/b.md", "docs/a.md", "docs/B.md", "docs/b/c.md")
          .map(_ -> failingMarkdown)
          .toMap
        Cli
          .program(List("docs"), tree(docs, texts))
          .map(outcome =>
            assertTrue(
              outcome.exitCode == 1,
              located(outcome) == List("docs/B.md", "docs/a.md", "docs/b/c.md", "docs/b.md")
            )
          )
      ,
      test("a directory among files: its files in its place"):
        val texts = List("z.md", "docs/scheduling.md", "a.md").map(_ -> failingMarkdown).toMap
        Cli
          .program(
            List("z.md", "docs", "a.md"),
            tree(Map("docs" -> List(file("scheduling.md"))), texts)
          )
          .map(outcome =>
            assertTrue(located(outcome) == List("z.md", "docs/scheduling.md", "a.md"))
          )
      ,
      test("a directory given with a trailing slash: no second slash in the names"):
        Cli
          .program(
            List("docs/"),
            tree(
              Map("docs/" -> List(file("scheduling.md"))),
              Map("docs/scheduling.md" -> failingMarkdown)
            )
          )
          .map(outcome => assertTrue(outcome.stdout.startsWith("docs/scheduling.md:1: ")))
      ,
      test(".md read as Markdown and .txt as plain text"):
        // A requirement in a code block: Markdown skips it, plain text checks the line.
        val fenced = "```\nThe scheduler lists events.\n```\n"
        Cli
          .program(
            List("docs"),
            tree(
              Map("docs" -> List(file("billing.txt"), file("scheduling.md"))),
              Map("docs/billing.txt" -> fenced, "docs/scheduling.md" -> fenced)
            )
          )
          .map(outcome =>
            assertTrue(outcome.exitCode == 1, located(outcome).distinct == List("docs/billing.txt"))
          )
      ,
      test("hidden entries, links, other entries, and other files are left alone"):
        val entries = List(
          file("scheduling.md"),
          file(".draft.md"),
          directory(".github"),
          Entry("keys.md", EntryKind.Link),
          Entry("feed.md", EntryKind.Other),
          file("package.json")
        )
        val unexpected = List(
          "docs/.draft.md",
          "docs/.github/template.md",
          "docs/keys.md",
          "docs/feed.md",
          "docs/package.json"
        )
        for
          names <- Ref.make(List.empty[String])
          docs = recordedTree(
            Map("docs" -> entries, "docs/.github" -> List(file("template.md"))),
            (("docs/scheduling.md" -> passing) :: unexpected.map(_ -> "{not json")).toMap,
            name => names.update(_ :+ name),
            _ => ZIO.unit
          )
          outcome <- Cli.program(List("docs"), docs)
          read <- names.get
        yield assertTrue(outcome.exitCode == 0, read == List("docs/scheduling.md"))
      ,
      test("the options apply to every file found"):
        for
          limits <- Ref.make(List.empty[Int])
          docs = recordedTree(
            Map("docs" -> List(file("scheduling.md"))),
            Map("docs/scheduling.md" -> passing),
            _ => ZIO.unit,
            most => limits.update(_ :+ most)
          )
          outcome <- Cli.program(
            List("--profile", "openspec", "--max-input-bytes", "1000", "docs"),
            docs
          )
          seen <- limits.get
        yield assertTrue(
          outcome.exitCode == 1,
          outcome.stdout.contains("wrong-case"),
          seen == List(1001)
        )
      ,
      test("a directory with nothing to check: status 2, the message, and nothing later read"):
        for
          names <- Ref.make(List.empty[String])
          docs = recordedTree(
            Map(
              "notes" -> List(file("package.json"), directory(".github")),
              "notes/.github" -> List(file("template.md"))
            ),
            Map("later.md" -> failingMarkdown),
            name => names.update(_ :+ name),
            _ => ZIO.unit
          )
          outcome <- Cli.program(List("notes", "later.md"), docs)
          read <- names.get
        yield assertTrue(
          outcome.exitCode == 2,
          outcome.stdout.isEmpty,
          outcome.stderr == "Cannot check notes: it holds no .md or .txt files\n",
          read.isEmpty
        )
      ,
      test("a subdirectory with nothing to check is no failure"):
        Cli
          .program(
            List("docs"),
            tree(
              Map("docs" -> List(file("scheduling.md"), directory("images")), "docs/images" -> Nil),
              Map("docs/scheduling.md" -> passing)
            )
          )
          .map(outcome => assertTrue(outcome.exitCode == 0, outcome.stderr.isEmpty))
      ,
      test("a subdirectory that can't be listed: status 2, its name, and nothing later read"):
        for
          names <- Ref.make(List.empty[String])
          docs = recordedTree(
            Map("docs" -> List(file("billing.md"), directory("private"))),
            Map("docs/billing.md" -> failingMarkdown, "later.md" -> failingMarkdown),
            name => names.update(_ :+ name),
            _ => ZIO.unit
          )
          outcome <- Cli.program(List("docs", "later.md"), docs)
          read <- names.get
        yield assertTrue(
          outcome.exitCode == 2,
          outcome.stdout.isEmpty,
          outcome.stderr == "Cannot read docs/private\n",
          read == List("docs/billing.md")
        )
      ,
      test("a subdirectory that can't be listed is logged with the platform's error"):
        val noon = Instant.parse("2026-11-04T12:00:00Z")
        val lines = ListBuffer.empty[String]
        ZIO
          .withLogger(Cli.logger(LogLevel.Warning, line => lines += line, () => noon))(
            Cli.program(List("docs"), tree(Map("docs" -> List(directory("private"))), Map.empty))
          )
          .as(
            assertTrue(
              lines.toList.exists(line =>
                line.contains(" ERROR Cannot read input ") && line.contains(unlistable) &&
                  line.contains(" exit=2")
              )
            )
          )
      ,
      test("a directory with nothing to check is logged"):
        val noon = Instant.parse("2026-11-04T12:00:00Z")
        val lines = ListBuffer.empty[String]
        ZIO
          .withLogger(Cli.logger(LogLevel.Warning, line => lines += line, () => noon))(
            Cli.program(List("notes"), tree(Map("notes" -> Nil), Map.empty))
          )
          .as(
            assertTrue(
              lines.toList.exists(line =>
                line.contains(" ERROR Nothing to check ") && line.contains(" exit=2")
              )
            )
          )
      ,
      test("a file found that can't be read: status 2 and its name"):
        Cli
          .program(List("docs"), tree(Map("docs" -> List(file("secret.md"))), Map.empty))
          .map(outcome =>
            assertTrue(outcome.exitCode == 2, outcome.stderr == "Cannot read docs/secret.md\n")
          )
    ),
    suite("the application")(
      test("run takes its arguments from the command line"):
        Cli
          .run(reading(Map.empty))
          .provide(ZLayer.succeed(ZIOAppArgs(Chunk("--help"))))
          .map(code => assertTrue(code.code == 0))
    ) @@ TestAspect.silent,
    suite("console")(
      test("a passing requirement: exit code 0 and nothing written"):
        for
          code <- Cli.cli(
            Nil,
            readingOf((_, _) => unreadable("ENOENT"), _ => ZIO.succeed(utf8(passing)))
          )
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(code.code == 0, out.mkString.isEmpty, err.mkString.isEmpty)
      ,
      test("--help: exit code 0 and the usage text on standard output"):
        for
          code <- Cli.cli(List("--help"), reading(Map.empty))
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(code.code == 0, out.mkString.contains("Usage"), err.mkString.isEmpty)
      ,
      test("--help after an option: the usage, exit code 0, and standard input unread"):
        // `reading` fails standard input, so reading it would write a message to standard error.
        for
          code <- Cli.cli(List("--output", "json", "--help"), reading(Map.empty))
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(code.code == 0, out.mkString.contains("Usage"), err.mkString.isEmpty)
      ,
      test("--help with a file: the reason and the usage on standard error, exit code 2, no read"):
        // `reading` fails every file, so reading notes.txt would say `Cannot read notes.txt`.
        for
          code <- Cli.cli(List("notes.txt", "--help"), reading(Map.empty))
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(
          code.code == 2,
          out.mkString.isEmpty,
          err.mkString.contains(
            "--help can't be given with input files: leave out --help, or name a file called --help after --."
          ),
          err.mkString.contains("Usage"),
          !err.mkString.contains("Cannot read")
        )
      ,
      test("bad arguments: exit code 2 and the usage text on standard error"):
        for
          code <- Cli.cli(List("--format", "yaml"), reading(Map.empty))
          out <- TestConsole.output
          err <- TestConsole.outputErr
        yield assertTrue(code.code == 2, out.mkString.isEmpty, err.mkString.contains("Usage"))
      ,
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
      test("a warning is logged at WARN with the input, its line, kind, and message"):
        val noon = Instant.parse("2026-11-04T12:00:00Z")
        val lines = ListBuffer.empty[String]
        val notes =
          "# Reminders\n\nEvents are listed weekly.\n\n| Name | Requirement |\n| --- | --- |\n" +
            "| A | The scheduler shall list upcoming events. |\n| B | x |\n| C | one | two | three |\n"
        val message =
          "This table row has 4 cells and its header 2, so GitHub doesn't show the last 2."
        ZIO
          .withLogger(Cli.logger(LogLevel.Warning, line => lines += line, () => noon))(
            run(Map("notes.md" -> ZIO.succeed(notes)), "notes.md")
          )
          .map(outcome =>
            val written = lines.toList
            assertTrue(
              outcome.exitCode == 0,
              written.size == 1,
              written.forall(_.startsWith("2026-11-04T12:00:00Z WARN ")),
              written.forall(_.contains(" file=notes.md ")),
              written.forall(_.contains(" line=9 ")),
              written.forall(_.contains(" kind=table-extra-cells ")),
              written.forall(_.contains(s" message=\"$message\""))
            )
          )
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
