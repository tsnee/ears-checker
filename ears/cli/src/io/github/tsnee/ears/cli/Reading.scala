package io.github.tsnee.ears.cli

import zio.IO

/** Why an input couldn't be read. The platforms' own errors aren't interpreted: their kinds and
  * wording differ between the JVM and Node, and can change with either's version, so the message
  * names only the input, and the log entry carries the platform's error. Invalid UTF-8 is the one
  * reason given, since this program decides it (`Utf8`), the same everywhere.
  */
enum ReadFailure derives CanEqual:
  case Unreadable
  case NotUtf8(line: Int, column: Int)
  case OtherEncoding(name: String)

object ReadFailure:

  extension (failure: ReadFailure)
    /** The message for standard error about the input named `source`. */
    def describe(source: String): String = failure match
      case Unreadable            => s"Cannot read $source"
      case NotUtf8(line, column) =>
        s"Cannot read $source: not valid UTF-8 at line $line, column $column"
      case OtherEncoding(name) => s"Cannot read $source: $name, not UTF-8"

    /** The log's description of a decoding failure, which no platform error describes. */
    def decodingError: String = failure match
      case NotUtf8(_, _)       => "invalid UTF-8"
      case OtherEncoding(name) => s"$name byte-order mark"
      case Unreadable          => "unreadable"

/** The platform's reading: a file by name, and standard input. Each fails with the kind of failure
  * and the platform's own description of the error, for the log.
  */
final case class Reading(
    file: String => IO[(ReadFailure, String), String],
    standardInput: IO[(ReadFailure, String), String]
)
