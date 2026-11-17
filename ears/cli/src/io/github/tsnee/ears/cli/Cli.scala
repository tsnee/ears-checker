package io.github.tsnee.ears.cli

import java.time.Instant

import cats.data.NonEmptyList
import io.github.tsnee.ears.markdown.Markdown
import zio.*

/** `ears-check [--format text|markdown|openspec] [--profile classic|openspec] [--output text|json]
  * [--verbose] [FILE...]`: reads each FILE in order, or standard input when there is none, and
  * exits with the status described on `Outcome`. The first file that can't be read or parsed ends
  * the run. Log entries go to standard error: warnings and errors, or every level with `--verbose`.
  *
  * The program for every platform; each platform's entry point supplies its `Reading`.
  */
object Cli:

  /** The application's only logger, replacing ZIO's own format: built once, before the run, from
    * the command line (for the level) and ZIO's Clock, so it also covers starting and exiting.
    */
  val bootstrap: ZLayer[ZIOAppArgs, Nothing, Unit] = bootstrapWith(writeStandardError)

  /** `bootstrap`, with each log line given to `writeLog`. */
  def bootstrapWith(writeLog: String => Unit): ZLayer[ZIOAppArgs, Nothing, Unit] =
    Runtime.removeDefaultLoggers ++ ZLayer
      .fromZIO(
        for
          arguments <- ZIOAppArgs.getArgs
          clock <- ZIO.clock
        yield loggerFor(
          arguments.toList,
          writeLog,
          () => Unsafe.unsafe(unsafe ?=> clock.unsafe.instant())
        )
      )
      .flatMap(built => Runtime.addLogger(built.get))

  /** The application's run: the command line's arguments, read through `reading`. The entry point
    * exits with the code.
    */
  def run(reading: Reading): ZIO[ZIOAppArgs, Nothing, ExitCode] =
    ZIOAppArgs.getArgs.flatMap(arguments => cli(arguments.toList, reading))

  /** Runs the checker and writes its outcome to the console; the caller exits with the code. Log
    * entries go to whatever logger is installed (`bootstrap`'s, when run as the application).
    */
  def cli(arguments: List[String], reading: Reading): UIO[ExitCode] =
    for
      outcome <- program(arguments, reading)
      _ <- Console.print(outcome.stdout).orDie
      _ <- Console.printError(outcome.stderr).orDie
    yield ExitCode(outcome.exitCode)

  /** The logger for a run with these arguments: warnings and up, or every level with `--verbose`.
    */
  def loggerFor(
      arguments: List[String],
      writeLog: String => Unit,
      now: () => Instant
  ): ZLogger[String, Unit] =
    val verbose = Settings.verbose(arguments)
    logger(Logging.minimumLevel(verbose), writeLog, now)

  /** Each entry at `minimum` or above as one line (see `Logging.line`), with its cause when it has
    * one, given to `writeLog`, stamped by `now`.
    */
  def logger(
      minimum: LogLevel,
      writeLog: String => Unit,
      now: () => Instant
  ): ZLogger[String, Unit] = new ZLogger[String, Unit]:
    def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
    ): Unit =
      if logLevel >= minimum then
        val details = annotations ++ Option.when(!cause.isEmpty)("cause" -> cause.prettyPrint)
        writeLog(Logging.line(now(), logLevel, message(), details))

  /** Everything but the console: `reading` supplies the files and standard input. Every run ends
    * with an entry giving its findings and exit status.
    */
  def program(arguments: List[String], reading: Reading): UIO[Outcome] =
    check(arguments, reading).tap(outcome =>
      annotated(
        Map(
          "findings" -> outcome.findings.toString,
          "warnings" -> outcome.warnings.toString,
          "exit" -> outcome.exitCode.toString
        )
      )(
        ZIO.logInfo("Finished")
      )
    )

  private def writeStandardError(line: String): Unit = java.lang.System.err.println(line)

  private def check(arguments: List[String], reading: Reading): UIO[Outcome] =
    val argumentText = Logging.argumentsDetail(arguments)
    Settings.parseInvocation(arguments) match
      case Left(Usage(text, true, _)) =>
        annotated(Map("arguments" -> argumentText))(ZIO.logInfo("Help requested")).as(
          Outcome(exitCode = 0, stdout = s"$text\n", stderr = "", findings = 0, warnings = 0)
        )
      case Left(Usage(text, false, reasons)) =>
        annotated(
          Map("arguments" -> argumentText, "reasons" -> reasons.mkString("; "), "exit" -> "2")
        )(
          ZIO.logError("Invalid arguments")
        ).as(Outcome(exitCode = 2, stdout = "", stderr = s"$text\n", findings = 0, warnings = 0))
      case Right(Invocation(verbose, all, limit)) =>
        // ZIO.foreach is sequential and stops at the first failure: no later file is read.
        val loaded =
          for
            first <- input(all.head, reading, limit)
            rest <- ZIO.foreach(all.tail)(input(_, reading, limit))
          yield first ++ rest.flatMap(_.toList)
        val details =
          Map(
            "arguments" -> argumentText,
            "inputs" -> all.size.toString,
            "verbose" -> verbose.toString,
            "max-input-bytes" -> limit.toString
          )
        // `inputs` counts the arguments; a directory stands for its files, counted once read.
        annotated(details)(ZIO.logDebug("Arguments")) *>
          loaded
            .tap(found =>
              annotated(Map("files" -> found.size.toString))(ZIO.logDebug("Read inputs"))
            )
            .either
            .map(Checker.outcome)

  /** One input as given: standard input or a file, loaded; or a directory, each file under it
    * loaded in turn (`walk`), failing if it holds none to check, so a mistyped or emptied folder
    * can't pass by checking nothing.
    */
  private def input(
      settings: Settings,
      reading: Reading,
      limit: Int
  ): IO[String, NonEmptyList[(Settings, Found)]] =
    settings.file
      .fold(ZIO.succeed(false))(reading.isDirectory)
      .flatMap(directory =>
        settings.file match
          // Annotated with the input's details, as a file's entries are; each file found annotates
          // its own name over them.
          case Some(name) if directory =>
            annotated(Logging.inputDetails(settings))(
              walk(settings, name, reading, limit).flatMap(found =>
                ZIO
                  .fromOption(NonEmptyList.fromList(found))
                  .orElse(
                    annotated(Map("directory" -> name, "exit" -> "2"))(
                      ZIO.logError("Nothing to check")
                    ) *> ZIO.fail(DirectoryWalk.nothingToCheck(name))
                  )
              )
            )
          case _ => load(settings, reading, limit).map(NonEmptyList.one)
      )

  /** Every file to check under `directory`, loaded in the order `DirectoryWalk` gives, depth first:
    * a subdirectory is listed when it's reached, so the first failure in the inputs' order is the
    * one reported and nothing after it is read. A directory that can't be listed fails as an
    * unreadable input.
    */
  private def walk(
      settings: Settings,
      directory: String,
      reading: Reading,
      limit: Int
  ): IO[String, List[(Settings, Found)]] =
    annotated(Map("directory" -> directory)) {
      for
        _ <- ZIO.logDebug("Listing directory")
        entries <- reading
          .entries(directory)
          .tapError(error =>
            annotated(Map("error" -> error, "exit" -> "2"))(ZIO.logError("Cannot read input"))
          )
          .mapError(_ => ReadFailure.Unreadable.describe(directory))
        steps = DirectoryWalk.steps(directory, entries)
        listing = DirectoryWalk.summary(steps)
        _ <- annotated(
          Map(
            "files" -> listing.files.toString,
            "subdirectories" -> listing.subdirectories.toString,
            "skipped" -> listing.skipped.toString
          )
        )(ZIO.logDebug("Listed directory"))
        _ <- ZIO.foreachDiscard(listing.noted)((path, reason) =>
          annotated(Map("entry" -> path, "reason" -> reason))(ZIO.logDebug("Skipped entry"))
        )
        found <- ZIO.foreach(steps) {
          case Step.Check(path) =>
            load(settings.copy(file = Some(path)), reading, limit).map(List(_))
          case Step.Enter(path) => walk(settings, path, reading, limit)
          case Step.Skip(_, _)  => ZIO.succeed(Nil)
        }
      yield found.flatten
    }

  /** One input read and split into requirements and its reader's warnings, each warning logged at
    * WARN, with its details on every entry it logs: for Markdown, those include the parser, or the
    * reason when it can't run.
    */
  private def load(
      settings: Settings,
      reading: Reading,
      limit: Int
  ): IO[String, (Settings, Found)] =
    ZIO
      .when(settings.inputFormat == InputFormat.Markdown)(ZIO.succeed(Markdown.parser()))
      .flatMap(parser =>
        annotated(Logging.inputDetails(settings) ++ Logging.parserDetails(parser)) {
          for
            _ <- ZIO.logDebug("Reading input")
            content <- read(settings, reading, limit)
            found <- ZIO
              .fromEither(Checker.parse(settings, content))
              .tapError(failure =>
                annotated(Map("error" -> "parse", "reason" -> failure.reason, "exit" -> "2"))(
                  ZIO.logError("Cannot parse input")
                )
              )
              .mapError(_.describe(settings.messageName))
            _ <- annotated(
              Map(
                "characters" -> content.length.toString,
                "requirements" -> found.candidates.size.toString,
                "warnings" -> found.warnings.size.toString
              )
            )(ZIO.logInfo("Read input"))
            _ <- ZIO.foreachDiscard(found.warnings)(warning =>
              annotated(Logging.warningDetails(warning))(ZIO.logWarning("Warning"))
            )
          yield settings -> found
        }
      )

  /** The input's text, or the message for standard error; the error itself is logged first. At most
    * the limit and one more byte is read: the extra byte tells an input of exactly the limit from a
    * larger one, without holding the larger one whole or waiting for a stream that never ends.
    */
  private def read(settings: Settings, reading: Reading, limit: Int): IO[String, String] =
    settings.file
      .fold(reading.standardInput(limit + 1))(reading.file(_, limit + 1))
      .mapError(error => ReadFailure.Unreadable -> error)
      .flatMap(bytes =>
        ZIO
          .fromEither(Bounded.decode(bytes, limit))
          .mapError(failure => failure -> failure.decodingError)
      )
      .tapError((failure, error) =>
        val details = failure match
          case ReadFailure.NotUtf8(line, column) => Map("position" -> s"$line:$column")
          case ReadFailure.OtherEncoding(name)   => Map("encoding" -> name)
          case ReadFailure.TooLarge(largest)     => Map("limit" -> largest.toString)
          case ReadFailure.Unreadable            => Map.empty
        annotated(Map("error" -> error, "exit" -> "2") ++ details)(
          ZIO.logError("Cannot read input")
        )
      )
      .mapError((failure, _) => failure.describe(settings.messageName))

  private def annotated[R, E, A](details: Map[String, String])(effect: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.logAnnotate(details.map(LogAnnotation(_, _)).toSet)(effect)
