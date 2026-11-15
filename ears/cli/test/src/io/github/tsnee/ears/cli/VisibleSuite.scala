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
    ,
    test("a lone high and a lone low surrogate become \\ud83d and \\ude00"):
      assertTrue(Visible.escape("a\ud83db\ude00c") == "a\\ud83db\\ude00c")
    ,
    test("a surrogate pair stays as it is"):
      assertTrue(Visible.escape("events 📅 😀.") == "events 📅 😀.")
    ,
    test("no lone surrogate is left"):
      check(Gen.listOfBounded(0, 20)(withSurrogates).map(_.mkString)) { text =>
        assertTrue(loneSurrogates(Visible.escape(text)).isEmpty)
      }
    ,
    test("text of surrogate pairs and characters left alone is unchanged"):
      check(Gen.listOfBounded(0, 20)(Gen.oneOf(pairs, plain)).map(_.mkString)) { text =>
        assertTrue(Visible.escape(text) == text)
      }
    ,
    test("each explicit bidirectional formatting character becomes \\u and its four digits"):
      assertTrue(
        bidirectional.forall(c => Visible.escape(c.toString) == "\\u" + Integer.toHexString(c))
      )
    ,
    test("a right-to-left override becomes \\u202e"):
      val text = "list " + 0x202e.toChar.toString + "events."
      assertTrue(Visible.escape(text) == "list \\u202eevents.")
    ,
    test("the implicit marks LRM, RLM, and ALM stay as they are"):
      val text = List(0x200e, 0x200f, 0x061c).map(_.toChar).mkString("a", "b", "c")
      assertTrue(Visible.escape(text) == text)
    ,
    test("no explicit bidirectional formatting character is left"):
      val withBidirectional = Gen.oneOf(characters, Gen.elements(bidirectional*))
      check(Gen.listOfBounded(0, 20)(withBidirectional).map(_.mkString)) { text =>
        assertTrue(!Visible.escape(text).exists(bidirectional.contains))
      }
  )

  /** Unicode's explicit bidirectional formatting characters, built from their code points so this
    * source holds none: LRE, RLE, PDF, LRO, RLO, LRI, RLI, FSI, and PDI.
    */
  private val bidirectional: List[Char] =
    ((0x202a to 0x202e) ++ (0x2066 to 0x2069)).toList.map(_.toChar)

  /** Surrogate pairs, built from their code points: the lowest and highest outside the Basic
    * Multilingual Plane and two emoji.
    */
  private val pairs: Gen[Any, String] =
    Gen.elements(0x10000, 0x10ffff, 0x1f4c5, 0x1f600).map(Character.toChars(_).mkString)

  /** One character `escape` leaves alone. */
  private val plain: Gen[Any, String] = characters.filterNot(acted).map(_.toString)

  /** The characters of `characters`, and surrogates, high and low, which a text then holds alone,
    * in pairs, and in runs.
    */
  private val withSurrogates: Gen[Any, Char] =
    Gen.oneOf(characters, Gen.elements('\ud83d', '\ud800', '\udbff', '\ude00', '\udc00', '\udfff'))

  /** The positions of surrogates that aren't half of a pair: a high one not followed by a low one,
    * or a low one not after a high one.
    */
  private def loneSurrogates(text: String): List[Int] =
    text.indices.toList.filter { i =>
      val c = text(i)
      (Character
        .isHighSurrogate(c) && !(i + 1 < text.length && Character.isLowSurrogate(text(i + 1)))) ||
      (Character.isLowSurrogate(c) && !(i > 0 && Character.isHighSurrogate(text(i - 1))))
    }
