package io.github.tsnee.ears.cli

import io.github.tsnee.ears.Profile
import zio.test.*

object SettingsSuite extends ZIOSpecDefault:

  private def file(name: String): Settings = Settings(Some(name), None, None, None)

  def spec = suite("Settings")(
    suite("parsing")(
      test("format option and file argument"):
        assertTrue(
          Settings.parse(List("--format", "markdown", "reqs.txt")) ==
            Right(Settings(Some("reqs.txt"), Some(InputFormat.Markdown), None, None))
        )
      ,
      test("profile option without a file"):
        assertTrue(
          Settings.parse(List("--profile", "openspec")) ==
            Right(Settings(None, None, Some(Profile.OpenSpec), None))
        )
      ,
      test("output option"):
        assertTrue(
          Settings.parse(List("--output", "json")) ==
            Right(Settings(None, None, None, Some(OutputFormat.Json)))
        )
      ,
      test("an unknown format is rejected"):
        assertTrue(Settings.parse(List("--format", "yaml")).isLeft)
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
