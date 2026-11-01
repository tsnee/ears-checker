package io.github.tsnee.ears.cli

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

/** Command-line settings as given. Unset options are resolved by the extensions below. */
final case class Settings(
    file: Option[String],
    formatOption: Option[InputFormat],
    profileOption: Option[Profile],
    outputOption: Option[OutputFormat]
) derives CanEqual

/** Usage text instead of settings: either the user asked for help, or the arguments were invalid.
  */
final case class Usage(text: String, requested: Boolean) derives CanEqual

object Settings:

  /** Settings from command-line arguments, or the usage text when help was requested or the
    * arguments don't parse. decline reports both as `Help`; only the invalid case carries errors.
    */
  def parse(arguments: List[String]): Either[Usage, Settings] =
    command.parse(arguments, Map.empty).left.map(help => Usage(help.toString, help.errors.isEmpty))

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

  private val fileOpt = Opts.argument[String]("file").orNone

  private val command = Command("ears-check", "Check requirements against the EARS templates.")(
    (fileOpt, formatOpt, profileOpt, outputOpt).mapN(Settings.apply)
  )
