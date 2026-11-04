package io.github.tsnee.ears.cli

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant

import cats.data.NonEmptyList
import zio.*

/** `ears-check [--format text|markdown|openspec] [--profile classic|openspec] [--output text|json]
  * [--verbose] [FILE...]`: reads each FILE in order, or standard input when there is none, and
  * exits with the status described on `Outcome`. The first file that can't be read or parsed ends
  * the run. Log entries go to standard error: warnings and errors, or every level with `--verbose`.
  */
object Main extends ZIOAppDefault:

  // ZIO's own logger writes in its own format; cliLogging installs this program's instead.
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Runtime.removeDefaultLoggers

  def run: ZIO[ZIOAppArgs, Nothing, Unit] =
    getArgs.flatMap(arguments => cli(arguments.toList, readAll(java.lang.System.in))).flatMap(exit)

  /** Runs the checker and writes its outcome to the console, logging to standard error; the caller
    * exits with the code.
    */
  def cli(arguments: List[String], stdin: Task[String]): UIO[ExitCode] =
    cliLogging(arguments, stdin, line => java.lang.System.err.println(line))

  /** As `cli`, with each log line given to `writeLog`. */
  def cliLogging(
      arguments: List[String],
      stdin: Task[String],
      writeLog: String => Unit
  ): UIO[ExitCode] =
    val verbose = Settings.parseInvocation(arguments).exists(_.verbose)
    ZIO.withLogger(logger(Logging.minimumLevel(verbose), writeLog)) {
      for
        outcome <- program(arguments, stdin)
        _ <- Console.print(outcome.stdout).orDie
        _ <- Console.printError(outcome.stderr).orDie
      yield ExitCode(outcome.exitCode)
    }

  /** Everything but the console: `stdin` supplies standard input when no file is given. */
  def program(arguments: List[String], stdin: Task[String]): UIO[Outcome] =
    val argumentText = arguments.mkString(" ")
    Settings.parseInvocation(arguments) match
      case Left(Usage(text, true, _)) =>
        ZIO.logDebug("Help requested").as(Outcome(0, s"$text\n", "", 0))
      case Left(Usage(text, false, reasons)) =>
        annotated(
          Map("arguments" -> argumentText, "reasons" -> reasons.mkString("; "), "exit" -> "2")
        )(
          ZIO.logError("Invalid arguments")
        ).as(Outcome(2, "", s"$text\n", 0))
      case Right(Invocation(verbose, all)) =>
        // ZIO.foreach is sequential and stops at the first failure: no later file is read.
        val loaded =
          for
            first <- load(all.head, stdin)
            rest <- ZIO.foreach(all.tail)(load(_, stdin))
          yield NonEmptyList(first, rest)
        val details =
          Map(
            "arguments" -> argumentText,
            "inputs" -> all.size.toString,
            "verbose" -> verbose.toString
          )
        for
          _ <- annotated(details)(ZIO.logDebug("Arguments"))
          found <- loaded.either
          outcome = Checker.outcome(found)
          _ <- ZIO.whenDiscard(found.isRight)(
            annotated(
              Map("findings" -> outcome.findings.toString, "exit" -> outcome.exitCode.toString)
            )(ZIO.logInfo("Finished"))
          )
        yield outcome

  /** One input read and split into requirements, with its details on every entry it logs. */
  private def load(
      settings: Settings,
      stdin: Task[String]
  ): IO[String, (Settings, List[Candidate])] =
    annotated(Logging.inputDetails(settings)) {
      for
        _ <- ZIO.logDebug("Reading input")
        content <- read(settings, stdin)
        candidates <- ZIO
          .fromEither(Checker.candidates(settings, content))
          .tapError(reason =>
            annotated(Map("error" -> "parse", "reason" -> reason, "exit" -> "2"))(
              ZIO.logError("Cannot parse input")
            )
          )
        _ <- annotated(
          Map("characters" -> content.length.toString, "requirements" -> candidates.size.toString)
        )(ZIO.logInfo("Read input"))
      yield settings -> candidates
    }

  /** The input's text, or the message for standard error; the error itself is logged first. */
  private def read(settings: Settings, stdin: Task[String]): IO[String, String] =
    val attempt = settings.file match
      case Some(name) =>
        ZIO.attemptBlocking(Files.readString(Paths.get(name), StandardCharsets.UTF_8))
      case None => stdin
    val source = settings.file.getOrElse("standard input")
    attempt
      .tapError(error =>
        annotated(Map("error" -> error.getClass.getName, "reason" -> reason(error), "exit" -> "2"))(
          ZIO.logError("Cannot read input")
        )
      )
      .mapError(error => s"Cannot read $source: ${describe(error)}")

  private def describe(error: Throwable): String = error match
    case _: java.nio.file.NoSuchFileException => "no such file"
    case other                                => reason(other)

  private def reason(error: Throwable): String =
    Option(error.getMessage).getOrElse(error.toString)

  private def annotated[R, E, A](details: Map[String, String])(effect: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.logAnnotate(details.map(LogAnnotation(_, _)).toSet)(effect)

  /** Writes each entry at `minimum` or above as one line (see `Logging.line`). */
  private def logger(minimum: LogLevel, writeLog: String => Unit): ZLogger[String, Unit] =
    new ZLogger[String, Unit]:
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
          writeLog(Logging.line(Instant.now(), logLevel, message(), annotations))

  /** Reads a whole stream as UTF-8. */
  def readAll(in: InputStream): Task[String] =
    ZIO.attemptBlocking(new String(in.readAllBytes(), StandardCharsets.UTF_8))
