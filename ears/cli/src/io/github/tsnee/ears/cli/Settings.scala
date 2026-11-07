package io.github.tsnee.ears.cli

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*
import com.monovore.decline.Command
import com.monovore.decline.Opts
import io.github.tsnee.ears.Profile

enum InputFormat derives CanEqual:
  case PlainText, Markdown, OpenSpecJson

enum OutputFormat derives CanEqual:
  case Text, Json

/** Command-line settings for one input as given. Unset options are resolved by the extensions
  * below.
  */
final case class Settings(
    file: Option[String],
    formatOption: Option[InputFormat],
    profileOption: Option[Profile],
    outputOption: Option[OutputFormat]
) derives CanEqual

/** The whole command line: the run-wide options and one settings per input. */
final case class Invocation(verbose: Boolean, inputs: NonEmptyList[Settings]) derives CanEqual

/** Usage text instead of settings: either the user asked for help, or the arguments were invalid.
  */
final case class Usage(text: String, requested: Boolean, reasons: List[String]) derives CanEqual

object Settings:

  /** One settings per file argument, in order, each with the shared options; or one settings with
    * no file, for standard input. The usage text instead when help was requested or the arguments
    * don't parse: decline reports both as `Help`, and only the invalid case carries errors.
    */
  def parse(arguments: List[String]): Either[Usage, NonEmptyList[Settings]] =
    parseInvocation(arguments).map(_.inputs)

  /** As `parse`, with the run-wide options too. */
  def parseInvocation(arguments: List[String]): Either[Usage, Invocation] =
    command
      .parse(arguments, Map.empty)
      .left
      .map(help => Usage(help.toString, help.errors.isEmpty, help.errors))

  given CanEqual[NonEmptyList[Settings], NonEmptyList[Settings]] = CanEqual.derived

  extension (settings: Settings)

    /** The selected format, or one inferred from the file name: .md is Markdown, .json is OpenSpec
      * JSON, anything else (including standard input) is plain text.
      */
    def inputFormat: InputFormat = settings.formatOption.getOrElse(
      settings.file match
        case Some(name) if name.endsWith(".md")   => InputFormat.Markdown
        case Some(name) if name.endsWith(".json") => InputFormat.OpenSpecJson
        case _                                    => InputFormat.PlainText
    )

    /** The selected profile, or OpenSpec for OpenSpec JSON and classic for everything else. */
    def profile: Profile = settings.profileOption.getOrElse(
      if inputFormat == InputFormat.OpenSpecJson then Profile.OpenSpec else Profile.Classic
    )

    def output: OutputFormat = settings.outputOption.getOrElse(OutputFormat.Text)

    /** The name findings are located in. */
    def sourceName: String = settings.file.getOrElse("<stdin>")

  private def choice[A](name: String, choices: Map[String, A])(
      value: String
  ): ValidatedNel[String, A] =
    Validated
      .fromOption(
        choices.get(value),
        s"Unknown $name \"$value\"; use ${choices.keys.toList.sorted.mkString(", ")}."
      )
      .toValidatedNel

  private val formatOpt = Opts
    .option[String](
      "format",
      "Input format: text, markdown, or openspec (JSON from openspec show)."
    )
    .mapValidated(
      choice(
        "format",
        Map(
          "text" -> InputFormat.PlainText,
          "markdown" -> InputFormat.Markdown,
          "openspec" -> InputFormat.OpenSpecJson
        )
      )
    )
    .orNone

  private val profileOpt = Opts
    .option[String]("profile", "Modal keyword case: classic (shall) or openspec (SHALL).")
    .mapValidated(
      choice("profile", Map("classic" -> Profile.Classic, "openspec" -> Profile.OpenSpec))
    )
    .orNone

  private val outputOpt = Opts
    .option[String]("output", "Output format: text or json.")
    .mapValidated(choice("output", Map("text" -> OutputFormat.Text, "json" -> OutputFormat.Json)))
    .orNone

  /** Every file argument, or a single `None` for standard input. */
  private val filesOpt: Opts[NonEmptyList[Option[String]]] =
    Opts
      .arguments[String]("file")
      // An empty name means different things to each platform: the working directory on the JVM,
      // no file on Node.
      .mapValidated(files =>
        if files.exists(_.isEmpty) then "A file name can't be empty.".invalidNel
        else files.validNel
      )
      .map(_.map(Option(_)))
      .withDefault(NonEmptyList.one(None))

  private val verboseOpt =
    Opts.flag("verbose", "Log every step to standard error, not only warnings and errors.").orFalse

  private val command = Command("ears-check", "Check requirements against the EARS templates.")(
    (filesOpt, formatOpt, profileOpt, outputOpt, verboseOpt).mapN {
      (files, format, profile, output, verbose) =>
        Invocation(verbose, files.map(Settings(_, format, profile, output)))
    }
  )
