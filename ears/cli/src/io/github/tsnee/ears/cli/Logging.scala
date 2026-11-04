package io.github.tsnee.ears.cli

import java.time.Instant

import io.github.tsnee.ears.Profile
import zio.LogLevel

/** How the command line logs, as pure functions: which levels a run shows and how an entry is
  * written. `Main` installs a logger built from these; nothing here does IO.
  */
private[cli] object Logging:

  /** Every level from debug with `--verbose`; otherwise warnings and errors only. */
  def minimumLevel(verbose: Boolean): LogLevel =
    if verbose then LogLevel.Debug else LogLevel.Warning

  /** One entry as one line: timestamp, level, message, and the details as `key=value` pairs sorted
    * by key. Backslashes and line breaks are escaped, so no entry can span or forge another line; a
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

  private def escape(text: String): String =
    text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

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
