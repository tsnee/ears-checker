package io.github.tsnee.ears.cli

import zio.IO

/** Why an input couldn't be read. The platforms' own errors aren't interpreted: their kinds and
  * wording differ between the JVM and Node, and can change with either's version, so the message
  * names only the input, and the log entry carries the platform's error. Invalid UTF-8 and an input
  * over the size limit are the reasons given, since this program decides them (`Utf8`, `Bounded`),
  * the same everywhere.
  */
enum ReadFailure derives CanEqual:
  case Unreadable
  case NotUtf8(line: Int, column: Int)
  case OtherEncoding(name: String)
  case TooLarge(limit: Int)

object ReadFailure:

  extension (failure: ReadFailure)
    /** The message for standard error about the input named `source`. */
    def describe(source: String): String = failure match
      case Unreadable            => s"Cannot read $source"
      case NotUtf8(line, column) =>
        s"Cannot read $source: not valid UTF-8 at line $line, column $column"
      case OtherEncoding(name) => s"Cannot read $source: $name, not UTF-8"
      case TooLarge(limit)     =>
        s"Cannot read $source: more than $limit bytes; --max-input-bytes raises the limit"

    /** The log's description of a failure no platform error describes. */
    def decodingError: String = failure match
      case NotUtf8(_, _)       => "invalid UTF-8"
      case OtherEncoding(name) => s"$name byte-order mark"
      case TooLarge(_)         => "more than the limit"
      case Unreadable          => "unreadable"

/** The platform's reading: at most a given number of bytes of a file by name, and of standard
  * input, and nothing more; the size limit and decoding are shared code's (`Cli`, `Bounded`). Each
  * fails with the platform's own description of the error, for the log.
  */
final case class Reading(
    file: (String, Int) => IO[String, Array[Byte]],
    standardInput: Int => IO[String, Array[Byte]]
)
