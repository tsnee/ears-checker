package io.github.tsnee.ears.cli

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*
import com.monovore.decline.Command
import com.monovore.decline.Help
import com.monovore.decline.Opts
import com.monovore.decline.Visibility
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

/** The whole command line: the run-wide options and one settings per input. `maxInputBytes` is the
  * most bytes any one input may hold.
  */
final case class Invocation(verbose: Boolean, inputs: NonEmptyList[Settings], maxInputBytes: Int)
    derives CanEqual

/** Usage text instead of settings: either the user asked for help, or the arguments were invalid.
  */
final case class Usage(text: String, requested: Boolean, reasons: List[String]) derives CanEqual

object Settings:

  /** One settings per file argument, in order, each with the shared options; or one settings with
    * no file, for standard input. The usage text instead when help was requested or the arguments
    * are invalid.
    */
  def parse(arguments: List[String]): Either[Usage, NonEmptyList[Settings]] =
    parseInvocation(arguments).map(_.inputs)

  /** As `parse`, with the run-wide options too. `--help` among the options asks for the usage, as
    * long as no input file is named: with one, the arguments are invalid, so a glob that picks up a
    * file named `--help` fails rather than checking nothing.
    */
  def parseInvocation(arguments: List[String]): Either[Usage, Invocation] =
    command.parse(arguments, Map.empty) match
      case Left(help)                      => Left(invalid(help.errors))
      case Right(Request(false, invoking)) => Right(invoking)
      case Right(Request(true, invoking))  =>
        if invoking.inputs.exists(_.file.isDefined) then Left(invalid(List(helpWithFiles)))
        else Left(Usage(Help.fromCommand(command).toString, requested = true, Nil))

  /** Whether the arguments ask for every log entry (`--verbose`), help or not. */
  def verbose(arguments: List[String]): Boolean =
    command.parse(arguments, Map.empty).exists(_.invocation.verbose)

  /** Why `--help` and an input file can't go together, and how to name a file `--help`. */
  private val helpWithFiles =
    "--help can't be given with input files: leave out --help, or name a file called --help after --."

  /** The usage text, with the reasons the arguments were rejected. The reasons quote the arguments,
    * which can come from file names a glob found: each is shown with its control characters as
    * \uXXXX (Visible), while the usage keeps its lines.
    */
  private def invalid(reasons: List[String]): Usage =
    Usage(
      Help.fromCommand(command).withErrors(reasons.map(Visible.escape)).toString,
      requested = false,
      reasons
    )

  /** What decline parses: whether `--help` was given, and the invocation otherwise. */
  private final case class Request(help: Boolean, invocation: Invocation)

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

    /** The input's name in a message about it, such as a read or parse error. */
    def messageName: String = settings.file.getOrElse("standard input")

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

  private val defaultInputLimit = 10 * 1024 * 1024

  private val largestInputLimit = 100 * 1024 * 1024

  /** The most bytes one input may hold: 10 MiB unless given, up to 100 MiB. Requirement documents
    * are kilobytes, so the default stops a hostile input long before memory matters, and the upper
    * bound keeps every size the reading and parsing compute (the limit and one byte, the Markdown
    * parser's offsets) far from `Int`'s range. Bytes only, so there's one unit to read.
    */
  private val maxInputBytesOpt =
    Opts
      .option[String](
        "max-input-bytes",
        s"Most bytes an input may hold, 1 to $largestInputLimit (default $defaultInputLimit).",
        metavar = "bytes"
      )
      .mapValidated(value =>
        Validated
          .fromOption(
            value.toIntOption.filter(n => n >= 1 && n <= largestInputLimit),
            s"--max-input-bytes takes a whole number from 1 to $largestInputLimit, not \"$value\"."
          )
          .toValidatedNel
      )
      .withDefault(defaultInputLimit)

  private val verboseOpt =
    Opts.flag("verbose", "Log every step to standard error, not only warnings and errors.").orFalse

  /** `--help` as an ordinary flag: decline's own help flag matches only as the whole command line,
    * so it's turned off below and this one is parsed like any other option. Listed among the
    * options but not in the usage line, as decline's own flag was (partial visibility).
    */
  private val helpOpt =
    Opts.flag("help", "Display this help text.", visibility = Visibility.Partial).orFalse

  private val command =
    Command("ears-check", "Check requirements against the EARS templates.", helpFlag = false)(
      (helpOpt, filesOpt, formatOpt, profileOpt, outputOpt, verboseOpt, maxInputBytesOpt).mapN {
        (help, files, format, profile, output, verbose, maxInputBytes) =>
          Request(
            help,
            Invocation(verbose, files.map(Settings(_, format, profile, output)), maxInputBytes)
          )
      }
    )
