package io.github.tsnee.ears.cli

import java.time.Instant

import io.github.tsnee.ears.Profile
import io.github.tsnee.ears.markdown.MarkdownUnavailable
import zio.LogLevel
import zio.json.*

/** How the command line logs, as pure functions: which levels a run shows and how an entry is
  * written. `Main` installs a logger built from these; nothing here does IO.
  */
private[cli] object Logging:

  /** Every level from debug with `--verbose`; otherwise warnings and errors only. */
  def minimumLevel(verbose: Boolean): LogLevel =
    if verbose then LogLevel.Debug else LogLevel.Warning

  /** One entry as one line: timestamp, level, message, and the details as `key=value` pairs sorted
    * by key. Backslashes, line breaks, and every other control or line-separator character are
    * escaped, so no entry can span or forge another line or send a terminal escape sequence; a
    * value that is empty or holds a space, `=`, or `"` is quoted.
    */
  def line(
      timestamp: Instant,
      level: LogLevel,
      message: String,
      details: Map[String, String]
  ): String =
    val pairs = details.toList.sortBy(_._1).map((key, value) => s" ${escape(key)}=${quote(value)}")
    s"$timestamp ${level.label} ${escape(message)}${pairs.mkString}"

  /** The details that identify one input: its name, format, profile, and output format. */
  def inputDetails(settings: Settings): Map[String, String] =
    Map(
      "file" -> settings.sourceName,
      "format" -> formatName(settings.inputFormat),
      "profile" -> profileName(settings.profile),
      "output" -> outputName(settings.output)
    )

  /** For a Markdown input, the parser that reads it, or, without one, every library tried. */
  def parserDetails(parser: Option[Either[MarkdownUnavailable, String]]): Map[String, String] =
    parser.fold(Map.empty)(
      _.fold(
        unavailable => Map("parser" -> "none", "attempts" -> unavailable.attempts.mkString("; ")),
        name => Map("parser" -> name)
      )
    )

  /** The details of one warning: its line, kind, and message. */
  def warningDetails(warning: Warning): Map[String, String] =
    Map(
      "line" -> warning.location.line.toString,
      "kind" -> warning.kind.name,
      "message" -> warning.kind.message
    )

  /** The command line exactly, as a JSON array, so arguments with spaces keep their boundaries. */
  def argumentsDetail(arguments: List[String]): String = arguments.toJson

  private def escape(text: String): String = text.flatMap {
    case '\\'                           => "\\\\"
    case '\n'                           => "\\n"
    case '\r'                           => "\\r"
    case c if separatesLines(c)         => f"\\u${c.toInt}%04x"
    case c if Character.isISOControl(c) => f"\\u${c.toInt}%04x"
    case c                              => c.toString
  }

  /** Unicode's line and paragraph separators, which some tools treat as line breaks. (U+0085, the
    * other one, is an ISO control character.)
    */
  private def separatesLines(c: Char): Boolean = c.toInt == 0x2028 || c.toInt == 0x2029

  private def quote(value: String): String =
    val escaped = escape(value)
    if escaped.isEmpty || escaped.exists(c => c == ' ' || c == '=' || c == '"') then
      "\"" + escaped.replace("\"", "\\\"") + "\""
    else escaped

  private def formatName(format: InputFormat): String = format match
    case InputFormat.PlainText    => "text"
    case InputFormat.Markdown     => "markdown"
    case InputFormat.OpenSpecJson => "openspec"

  private def profileName(profile: Profile): String = profile match
    case Profile.Classic  => "classic"
    case Profile.OpenSpec => "openspec"

  private def outputName(output: OutputFormat): String = output match
    case OutputFormat.Text => "text"
    case OutputFormat.Json => "json"
