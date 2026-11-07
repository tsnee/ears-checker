package io.github.tsnee.ears.cli

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Input is UTF-8, whatever the locale: the formats say so (JSON's RFC 8259, CommonMark's Unicode
  * text), and a locale is a poor guide to a file's encoding. Bytes that aren't valid UTF-8 are
  * rejected, never replaced, so a file in another encoding can't pass unread. Every platform
  * decodes here, so they agree on what's invalid and where.
  */
object Utf8:

  /** The text, or `NotUtf8` at the first byte that isn't valid UTF-8. */
  def decode(bytes: Array[Byte]): Either[ReadFailure, String] =
    val decoder = StandardCharsets.UTF_8
      .newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
    val in = ByteBuffer.wrap(bytes)
    // UTF-8 never takes fewer bytes than UTF-16 takes chars.
    val out = CharBuffer.allocate(bytes.length)
    val result = decoder.decode(in, out, true)
    if result.isError then Left(position(bytes, in.position()))
    else
      val _ = decoder.flush(out)
      Right(out.flip().toString)

  /** The line and column of `offset`; everything before it is valid UTF-8. The column counts
    * characters (code points), as an editor's does: every byte but a continuation byte starts one.
    */
  private def position(bytes: Array[Byte], offset: Int): ReadFailure =
    val before = bytes.take(offset)
    val lineStart = before.lastIndexOf('\n'.toByte) + 1
    val line = before.count(_ == '\n'.toByte) + 1
    val column = before.drop(lineStart).count(b => (b & 0xc0) != 0x80) + 1
    ReadFailure.NotUtf8(line, column)
