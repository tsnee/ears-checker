package io.github.tsnee.ears.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

import cats.data.NonEmptyList
import zio.*

/** `ears-check [--format text|markdown|openspec] [--profile classic|openspec] [--output text|json]
  * [FILE...]`: reads each FILE in order, or standard input when there is none, and exits with the
  * status described on `Outcome`. The first file that can't be read or parsed ends the run.
  */
object Main extends ZIOAppDefault:

   def run: ZIO[ZIOAppArgs, Nothing, Unit] =
    for
      arguments <- getArgs
      outcome <- program(arguments.toList, readStdin)
      _ <- Console.print(outcome.stdout).orDie
      _ <- Console.printError(outcome.stderr).orDie
      _ <- exit(ExitCode(outcome.exitCode))
    yield ()

  /** Everything but the console: `stdin` supplies standard input when no file is given. */
  def program(arguments: List[String], stdin: Task[String]): UIO[Outcome] =
    Settings.parse(arguments) match
      case Left(Usage(text, true))  => ZIO.succeed(Outcome(0, s"$text\n", ""))
      case Left(Usage(text, false)) => ZIO.succeed(Outcome(2, "", s"$text\n"))
      case Right(all)               =>
        val load = (settings: Settings) =>
          read(settings, stdin)
            .flatMap(content => ZIO.fromEither(Checker.candidates(settings, content)))
            .map(settings -> _)
        // ZIO.foreach is sequential and stops at the first failure: no later file is read.
        val loaded =
          for
            first <- load(all.head)
            rest <- ZIO.foreach(all.tail)(load)
          yield NonEmptyList(first, rest)
        loaded.either.map(Checker.outcome)

  private def read(settings: Settings, stdin: Task[String]): IO[String, String] =
    settings.file match
      case Some(name) =>
        ZIO
          .attemptBlocking(Files.readString(Paths.get(name), StandardCharsets.UTF_8))
          .mapError(error => s"Cannot read $name: ${describe(error)}")
      case None => stdin.mapError(error => s"Cannot read standard input: ${describe(error)}")

  private def describe(error: Throwable): String = error match
    case _: java.nio.file.NoSuchFileException => "no such file"
    case other                                => Option(other.getMessage).getOrElse(other.toString)

  private val readStdin: Task[String] =
    ZIO.attemptBlocking(new String(java.lang.System.in.readAllBytes(), StandardCharsets.UTF_8))
