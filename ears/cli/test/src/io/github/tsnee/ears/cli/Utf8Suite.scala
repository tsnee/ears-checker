package io.github.tsnee.ears.cli

import java.nio.charset.StandardCharsets

import zio.test.*

/** Strict UTF-8 decoding, with the position of the first invalid byte: the same on every platform.
  */
object Utf8Suite extends ZIOSpecDefault:

  private def bytes(values: Int*): Array[Byte] = values.map(_.toByte).toArray

  private def ascii(text: String): Seq[Int] = text.map(_.toInt)

  /** Any text with no lone surrogate, which UTF-8 can't encode. */
  private val text: Gen[Any, String] =
    Gen
      .listOf(
        Gen.oneOf(
          Gen.char.filter(c => !Character.isSurrogate(c)).map(_.toString),
          Gen.int(0x10000, 0x10ffff).map(point => String(Character.toChars(point)))
        )
      )
      .map(_.mkString)

  def spec = suite("UTF-8")(
    test("valid UTF-8 decodes to its text"):
      check(text)(original =>
        assertTrue(Utf8.decode(original.getBytes(StandardCharsets.UTF_8)) == Right(original))
      )
    ,
    test("a UTF-8 byte-order mark is dropped"):
      assertTrue(Utf8.decode(bytes(0xef, 0xbb, 0xbf, 0x61)) == Right("a"))
    ,
    test("a Latin-1 byte: its line and column"):
      assertTrue(
        Utf8.decode(bytes(ascii("ab\nl")*) ++ bytes(0xe9) ++ bytes(ascii("vent\n")*)) ==
          Left(ReadFailure.NotUtf8(2, 2))
      )
    ,
    test("the column counts characters, not bytes"):
      assertTrue(Utf8.decode(bytes(0xc3, 0xa9, 0xff)) == Left(ReadFailure.NotUtf8(1, 2)))
    ,
    test("a UTF-16 byte-order mark names UTF-16"):
      assertTrue(
        Utf8.decode(bytes(0xff, 0xfe, 0x61, 0x00)) == Left(ReadFailure.OtherEncoding("UTF-16")),
        Utf8.decode(bytes(0xfe, 0xff, 0x00, 0x61)) == Left(ReadFailure.OtherEncoding("UTF-16"))
      )
    ,
    test("a UTF-32 byte-order mark names UTF-32"):
      assertTrue(
        Utf8.decode(bytes(0xff, 0xfe, 0x00, 0x00, 0x61, 0x00, 0x00, 0x00)) ==
          Left(ReadFailure.OtherEncoding("UTF-32")),
        Utf8.decode(bytes(0x00, 0x00, 0xfe, 0xff, 0x00, 0x00, 0x00, 0x61)) ==
          Left(ReadFailure.OtherEncoding("UTF-32"))
      )
    ,
    test("after a UTF-8 byte-order mark, positions count from the text"):
      assertTrue(
        Utf8.decode(bytes(0xef, 0xbb, 0xbf, 0x61, 0xff)) == Left(ReadFailure.NotUtf8(1, 2))
      )
    ,
    test("a sequence cut off at the end"):
      assertTrue(
        Utf8.decode(bytes(ascii("abc")*) ++ bytes(0xe2, 0x82)) == Left(ReadFailure.NotUtf8(1, 4))
      )
    ,
    test("an overlong encoding"):
      assertTrue(Utf8.decode(bytes(0xc0, 0xaf)) == Left(ReadFailure.NotUtf8(1, 1)))
    ,
    test("an encoded surrogate"):
      assertTrue(Utf8.decode(bytes(0x61, 0xed, 0xa0, 0x80)) == Left(ReadFailure.NotUtf8(1, 2)))
  )
