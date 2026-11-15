package io.github.tsnee.ears.cli

/** What was read of an input, checked against the size limit before it's decoded. `Cli` asks a
  * `Reading` for the limit and one more byte, so more than `limit` bytes means the input is larger
  * than the limit, whatever its real size.
  */
object Bounded:

  /** `TooLarge` for more than `limit` bytes; otherwise the text, decoded strictly (`Utf8`). */
  def decode(bytes: Array[Byte], limit: Int): Either[ReadFailure, String] =
    if bytes.length > limit then Left(ReadFailure.TooLarge(limit)) else Utf8.decode(bytes)
