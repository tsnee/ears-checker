package io.github.tsnee.ears.cli

import io.github.tsnee.ears.cli.TerminalText.acted
import io.github.tsnee.ears.cli.TerminalText.characters
import io.github.tsnee.ears.cli.TerminalText.texts
import zio.test.*

/** What text output and messages show of characters a terminal would act on (`TerminalText`). */
object VisibleSuite extends ZIOSpecDefault:

  def spec = suite("Visible")(
    test("the escape character becomes \\u001b"):
      assertTrue(Visible.escape("events \u001b[2K.") == "events \\u001b[2K.")
    ,
    test("BEL, NEL, and the line separator become \\uXXXX"):
      assertTrue(Visible.escape("a\u0007b\u0085c\u2028d") == "a\\u0007b\\u0085c\\u2028d")
    ,
    test("backslashes and other text are unchanged"):
      val text = "When a file arrives in C:\\orders\\new, the café shall queue it \\u001b."
      assertTrue(Visible.escape(text) == text)
    ,
    test("nothing a terminal acts on is left"):
      check(texts)(text => assertTrue(!Visible.escape(text).exists(acted)))
    ,
    test("text without such characters is unchanged"):
      check(texts.map(_.filterNot(acted)))(text => assertTrue(Visible.escape(text) == text))
    ,
    test("each such character becomes \\u and four lowercase hexadecimal digits"):
      // The expected text is built without a format string, so it can't share the code's mistake.
      check(characters.filter(acted)) { c =>
        val digits = Integer.toHexString(c.toInt).reverse.padTo(4, '0').reverse
        assertTrue(Visible.escape(c.toString) == "\\u" + digits)
      }
  )
