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
      // Without `#`, since two side by side are escaped (a test below covers them).
      check(texts.map(_.filterNot(c => acted(c) || c == '#'))) { text =>
        assertTrue(Visible.escape(text) == text)
      }
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
    test("a surrogate pair is written as two escapes"):
      assertTrue(
        Visible.escape(
          "events " + emoji(0x1f4c5) + " " + emoji(0x1f600) + "."
        ) == "events \\ud83d\\udcc5 \\ud83d\\ude00."
      )
    ,
    test("no lone surrogate is left"):
      check(Gen.listOfBounded(0, 20)(withSurrogates).map(_.mkString)) { text =>
        assertTrue(loneSurrogates(Visible.escape(text)).isEmpty)
      }
    ,
    test("each surrogate pair becomes \\u and each of its units' four digits"):
      check(pairs) { pair =>
        val units = pair.toList.map(c => "\\u" + Integer.toHexString(c.toInt))
        assertTrue(Visible.escape(pair) == units.mkString)
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
    test("the implicit marks LRM, RLM, and ALM are escaped"):
      val text = List(0x200e, 0x200f, 0x061c).map(_.toChar).mkString("a", "b", "c")
      assertTrue(Visible.escape(text) == "a\\u200eb\\u200fb\\u061cc")
    ,
    test("no explicit bidirectional formatting character is left"):
      val withBidirectional = Gen.oneOf(characters, Gen.elements(bidirectional*))
      check(Gen.listOfBounded(0, 20)(withBidirectional).map(_.mkString)) { text =>
        assertTrue(!Visible.escape(text).exists(bidirectional.contains))
      }
    ,
    test("the ends of each range of the English character set stay as they are"):
      val ends = List(0x20, 0x7e, 0xa0, 0xac, 0xae, 0xff, 0x300, 0x36f, 0x2010, 0x2022, 0x2026,
        0x2030, 0x2032, 0x2033, 0x2039, 0x203a, 0x20ac, 0x2122)
      assertTrue(ends.map(_.toChar.toString).forall(c => Visible.escape(c) == c))
    ,
    test("the neighbors of those ranges become \\u and four lowercase hexadecimal digits"):
      val neighbors = List(0x1f, 0x7f, 0x9f, 0xad, 0x100, 0x2ff, 0x370, 0x200f, 0x2023, 0x2025,
        0x2027, 0x2031, 0x2034, 0x2038, 0x203b, 0x20ab, 0x20ad, 0x2121, 0x2123)
      assertTrue(
        neighbors.forall(p => Visible.escape(p.toChar.toString) == "\\u" + hexDigits(p.toChar))
      )
    ,
    test("an emoji becomes the escapes of its two units"):
      assertTrue(Visible.escape(Character.toChars(0x1f600).mkString) == "\\ud83d\\ude00")
    ,
    test("the first of two number signs becomes \\u0023, and a lone one stays"):
      assertTrue(
        Visible.escape("a##[error]b") == "a\\u0023#[error]b",
        Visible.escape("###") == "\\u0023\\u0023#",
        Visible.escape("issue #12") == "issue #12"
      )
    ,
    test("nothing outside the English character set and no two number signs are left"):
      val units = Gen.oneOf(
        characters,
        Gen.const('#'),
        Gen.elements(englishRanges.flatMap((low, high) => List(low, high)).map(_.toChar)*),
        Gen.int(0, 0xffff).map(_.toChar)
      )
      check(Gen.listOfBounded(0, 20)(units).map(_.mkString)) { text =>
        val escaped = Visible.escape(text)
        assertTrue(escaped.forall(english), !escaped.contains("##"))
      }
  )

  /** The English character set, as code ranges: printable ASCII, Latin-1 without the soft hyphen,
    * the combining accents, and the typographic punctuation word processors insert. Written out
    * here, apart from `Visible`, so a test can't share the code's mistake.
    */
  private val englishRanges: List[(Int, Int)] = List(
    0x20 -> 0x7e,
    0xa0 -> 0xac,
    0xae -> 0xff,
    0x300 -> 0x36f,
    0x2010 -> 0x2022,
    0x2026 -> 0x2026,
    0x2030 -> 0x2030,
    0x2032 -> 0x2033,
    0x2039 -> 0x203a,
    0x20ac -> 0x20ac,
    0x2122 -> 0x2122
  )

  private def english(c: Char): Boolean =
    englishRanges.exists((low, high) => c.toInt >= low && c.toInt <= high)

  /** A character above U+FFFF, built from its code point so this source holds none. */
  private def emoji(codePoint: Int): String = Character.toChars(codePoint).mkString

  /** A code unit's four lowercase hexadecimal digits, built without a format string. */
  private def hexDigits(c: Char): String =
    Integer.toHexString(c.toInt).reverse.padTo(4, '0').reverse

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
