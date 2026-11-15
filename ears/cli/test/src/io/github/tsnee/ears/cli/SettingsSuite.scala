package io.github.tsnee.ears.cli

import cats.data.NonEmptyList
import io.github.tsnee.ears.Profile
import zio.test.*

object SettingsSuite extends ZIOSpecDefault:

  private def file(name: String): Settings = Settings(Some(name), None, None, None)

  /** Whether the arguments ask for help: the usage, requested, with no reasons to reject them. */
  private def asksForHelp(arguments: String*): Boolean =
    Settings.parseInvocation(arguments.toList) match
      case Left(usage) => usage.requested && usage.reasons.isEmpty
      case Right(_)    => false

  /** The reasons the arguments were rejected, or None when they weren't. */
  private def rejection(arguments: String*): Option[List[String]] =
    Settings.parseInvocation(arguments.toList) match
      case Left(usage) if !usage.requested => Some(usage.reasons)
      case _                               => None

  private val helpWithFiles =
    "--help can't be given with input files: leave out --help, or name a file called --help after --."

  def spec = suite("Settings")(
    suite("--help among the options")(
      test("alone"):
        assertTrue(asksForHelp("--help"))
      ,
      test("after an option"):
        assertTrue(asksForHelp("--output", "json", "--help"))
      ,
      test("before an option"):
        assertTrue(asksForHelp("--help", "--verbose"))
      ,
      test("after a file: rejected, saying how to name such a file"):
        assertTrue(rejection("notes.txt", "--help") == Some(List(helpWithFiles)))
      ,
      test("before a file, as a glob orders a file named --help: rejected"):
        assertTrue(rejection("--help", "notes.txt") == Some(List(helpWithFiles)))
      ,
      test("as an option's value: that option's value, so an invalid format"):
        assertTrue(rejection("--format", "--help").exists(_.exists(_.contains("--help"))))
      ,
      test("after --, it's a file name"):
        assertTrue(
          Settings.parseInvocation(List("--", "--help")) ==
            Right(Invocation(verbose = false, NonEmptyList.one(file("--help"))))
        )
      ,
      test("--verbose counts with --help, for the log"):
        assertTrue(
          Settings.verbose(List("--verbose", "--help")),
          !Settings.verbose(List("--help"))
        )
      ,
      test("the usage text lists --help"):
        assertTrue(
          Settings.parseInvocation(List("--help")).left.map(_.text.contains("--help")) ==
            Left(true)
        )
      ,
      test("the usage line is as before: --help among the options, not in the line"):
        assertTrue(
          Settings.parseInvocation(List("--help")).left.map(_.text.linesIterator.next()) ==
            Left(
              "Usage: ears-check [--format <string>] [--profile <string>] [--output <string>] " +
                "[--verbose] [<file>...]"
            )
        )
      ,
      test("beside an invalid option: rejected for that option, not shown as help"):
        assertTrue(rejection("--help", "--format", "yaml").exists(_.exists(_.contains("yaml"))))
    ),
    suite("parsing")(
      test("format option and file argument"):
        assertTrue(
          Settings.parse(List("--format", "markdown", "reqs.txt")) ==
            Right(
              NonEmptyList.one(Settings(Some("reqs.txt"), Some(InputFormat.Markdown), None, None))
            )
        )
      ,
      test("profile option without a file"):
        assertTrue(
          Settings.parse(List("--profile", "openspec")) ==
            Right(NonEmptyList.one(Settings(None, None, Some(Profile.OpenSpec), None)))
        )
      ,
      test("output option"):
        assertTrue(
          Settings.parse(List("--output", "json")) ==
            Right(NonEmptyList.one(Settings(None, None, None, Some(OutputFormat.Json))))
        )
      ,
      test("an unknown format is rejected"):
        assertTrue(Settings.parse(List("--format", "yaml")).isLeft)
      ,
      test("an empty file name is rejected, even among others"):
        assertTrue(
          Settings.parse(List("notes.md", "")) match
            case Left(Usage(_, false, reasons)) => reasons == List("A file name can't be empty.")
            case _                              => false
        )
      ,
      test("several files: one settings per file, in order, each with the shared options"):
        val json = Some(OutputFormat.Json)
        assertTrue(
          Settings.parse(List("--output", "json", "notes.md", "change.json")) ==
            Right(
              NonEmptyList.of(
                Settings(Some("notes.md"), None, None, json),
                Settings(Some("change.json"), None, None, json)
              )
            )
        )
      ,
      test("no file: one settings for standard input"):
        assertTrue(Settings.parse(Nil) == Right(NonEmptyList.one(Settings(None, None, None, None))))
    ),
    suite("invocation")(
      test("without --verbose: not verbose, with the inputs and their options"):
        assertTrue(
          Settings.parseInvocation(List("--output", "json", "notes.md")) ==
            Right(
              Invocation(
                verbose = false,
                NonEmptyList.one(
                  Settings(
                    file = Some("notes.md"),
                    formatOption = None,
                    profileOption = None,
                    outputOption = Some(OutputFormat.Json)
                  )
                )
              )
            )
        )
      ,
      test("--verbose before the files"):
        assertTrue(
          Settings.parseInvocation(List("--verbose", "notes.md")) ==
            Right(Invocation(verbose = true, NonEmptyList.one(file("notes.md"))))
        )
      ,
      test("--verbose after the files"):
        assertTrue(
          Settings.parseInvocation(List("notes.md", "change.json", "--verbose")).map(_.verbose) ==
            Right(true)
        )
      ,
      test("parse accepts --verbose and returns only the inputs"):
        assertTrue(
          Settings.parse(List("--verbose", "notes.md")) == Right(NonEmptyList.one(file("notes.md")))
        )
      ,
      test("the usage text lists --verbose"):
        assertTrue(
          Settings.parseInvocation(List("--help")).left.map(_.text.contains("--verbose")) ==
            Left(true)
        )
    ),
    suite("input format")(
      test("Markdown is inferred from a file name ending in .md"):
        assertTrue(file("requirements.md").inputFormat == InputFormat.Markdown)
      ,
      test("OpenSpec JSON is inferred from a file name ending in .json"):
        assertTrue(file("change.json").inputFormat == InputFormat.OpenSpecJson)
      ,
      test("plain text otherwise, including standard input"):
        assertTrue(
          file("notes.txt").inputFormat == InputFormat.PlainText,
          Settings(None, None, None, None).inputFormat == InputFormat.PlainText
        )
      ,
      test("a selected format wins over the file name"):
        assertTrue(
          Settings(Some("reqs.md"), Some(InputFormat.PlainText), None, None).inputFormat ==
            InputFormat.PlainText
        )
    ),
    suite("profile")(
      test("OpenSpec JSON defaults to the OpenSpec profile"):
        assertTrue(file("change.json").profile == Profile.OpenSpec)
      ,
      test("plain text and Markdown default to the classic profile"):
        assertTrue(
          file("notes.txt").profile == Profile.Classic,
          file("reqs.md").profile == Profile.Classic
        )
      ,
      test("a selected profile wins"):
        assertTrue(
          Settings(Some("notes.txt"), None, Some(Profile.OpenSpec), None).profile ==
            Profile.OpenSpec
        )
    ),
    suite("output")(
      test("text output by default"):
        assertTrue(Settings(None, None, None, None).output == OutputFormat.Text)
    )
  )
