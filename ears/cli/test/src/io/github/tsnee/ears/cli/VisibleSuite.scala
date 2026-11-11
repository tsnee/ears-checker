package io.github.tsnee.ears.cli

import zio.test.*

/** What text output and messages show of characters a terminal would act on. */
object VisibleSuite extends ZIOSpecDefault:

  /** A character a terminal or a line-based tool could act on: what `Visible` escapes. */
  private def acted(c: Char): Boolean =
    Character.isISOControl(c) || c.toInt == 0x2028 || c.toInt == 0x2029

  /** Characters mixing the escaped kinds (C0, DEL, C1, the separators) with plain text,
    * backslashes, and a no-break space.
    */
  private val characters = Gen.oneOf(
    Gen.char('\u0000', '\u001f'),
    Gen.char('\u007f', '\u009f'),
    Gen.elements('\u2028', '\u2029', '\\', 'u', '0', 'é', '\u00a0'),
    Gen.char(' ', '~')
  )
  private val texts = Gen.listOfBounded(0, 20)(characters).map(_.mkString)

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
