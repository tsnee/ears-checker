package io.github.tsnee.ears.cli

import zio.test.*

/** Why an input couldn't be parsed in its format: its message names the input, as a read error's
  * does, and its reason, for the log, is the parser's own account alone.
  */
object ParseFailureSuite extends ZIOSpecDefault:

  def spec = suite("parse failures")(
    test("not OpenSpec JSON: the message names the input; the reason is the parser's"):
      val failure = ParseFailure.NotOpenSpec("(expected '\"' got 'n')")
      assertTrue(
        failure.describe("notes.json") ==
          "Cannot parse notes.json: not OpenSpec change or spec JSON: (expected '\"' got 'n')",
        failure.reason == "(expected '\"' got 'n')"
      )
    ,
    test("Markdown the parser couldn't read: the message names the input"):
      val failure = ParseFailure.UnreadMarkdown("cmark-gfm returned nothing")
      assertTrue(
        failure.describe("notes.md") == "Cannot parse notes.md: cmark-gfm returned nothing",
        failure.reason == "cmark-gfm returned nothing"
      )
  )
