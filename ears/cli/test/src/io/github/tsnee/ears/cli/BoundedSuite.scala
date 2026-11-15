package io.github.tsnee.ears.cli

import java.nio.charset.StandardCharsets

import zio.test.*

/** What was read of an input, checked against the size limit: the same on every platform. */
object BoundedSuite extends ZIOSpecDefault:

  private def bytes(text: String): Array[Byte] = text.getBytes(StandardCharsets.UTF_8)

  def spec = suite("Bounded")(
    test("exactly the limit is decoded"):
      assertTrue(Bounded.decode(bytes("abc"), 3) == Right("abc"))
    ,
    test("one byte more than the limit is too large, naming the limit"):
      assertTrue(Bounded.decode(bytes("abcd"), 3) == Left(ReadFailure.TooLarge(3)))
    ,
    test("an empty input is decoded"):
      assertTrue(Bounded.decode(Array.empty[Byte], 1) == Right(""))
    ,
    test("invalid UTF-8 within the limit is still reported where it is"):
      val latin1 = bytes("ab\nl") ++ Array(0xe9.toByte) ++ bytes("vent\n")
      assertTrue(Bounded.decode(latin1, 100) == Left(ReadFailure.NotUtf8(2, 2)))
    ,
    test("an input is too large exactly when it holds more bytes than the limit"):
      check(Gen.int(0, 50), Gen.int(1, 50)) { (size, limit) =>
        val tooLarge = Bounded.decode(Array.fill(size)('a'.toByte), limit) ==
          Left(ReadFailure.TooLarge(limit))
        assertTrue(tooLarge == (size > limit))
      }
  )
