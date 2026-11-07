package io.github.tsnee.ears.cli

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

import zio.*

/** The JVM's entry point: `Cli` with files and standard input read through `java.nio`, and standard
  * output and error written as UTF-8, as on Node. (The JVM's own streams use the locale's charset,
  * which turns é into ? under the C locale.)
  */
object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    ZLayer
      .fromZIO(ZIO.succeed(utf8(FileOutputStream(FileDescriptor.err))))
      .flatMap(err => Cli.bootstrapWith(line => err.get[PrintStream].println(line)))

  def run: ZIO[ZIOAppArgs, Nothing, Unit] =
    ZIO
      .succeed(
        utf8Console(FileOutputStream(FileDescriptor.out), FileOutputStream(FileDescriptor.err))
      )
      .flatMap(console => ZIO.withConsole(console)(Cli.run(reading(readAll(java.lang.System.in)))))
      .flatMap(exit)

  /** Files by path, decoded strictly as UTF-8 (see `Utf8`), and `standardInput`'s text (see
    * `readAll`). The JVM's exceptions aren't interpreted (see `ReadFailure`): each is logged whole.
    */
  def reading(standardInput: Task[String]): Reading =
    Reading(
      name =>
        ZIO
          .attemptBlocking(Files.readAllBytes(Paths.get(name)))
          .mapError(error => ReadFailure.Unreadable -> error.toString)
          .flatMap(bytes => ZIO.fromEither(Utf8.decode(bytes)).mapError(_ -> "invalid UTF-8")),
      standardInput.mapError {
        case Undecodable(failure) => failure -> "invalid UTF-8"
        case error                => ReadFailure.Unreadable -> error.toString
      }
    )

  /** Reads a whole stream, decoded strictly as UTF-8 (see `Utf8`). */
  def readAll(in: InputStream): Task[String] =
    ZIO
      .attemptBlocking(in.readAllBytes())
      .flatMap(bytes => ZIO.fromEither(Utf8.decode(bytes)).mapError(Undecodable(_)))

  /** A console that writes `out` and `err` as UTF-8, and reads as ZIO's own does. */
  def utf8Console(out: OutputStream, err: OutputStream): Console =
    val (outStream, errStream) = (utf8(out), utf8(err))
    new Console:
      def print(line: => Any)(using Trace): IO[IOException, Unit] =
        ZIO.attemptBlockingIO(outStream.print(line.toString))
      def printError(line: => Any)(using Trace): IO[IOException, Unit] =
        ZIO.attemptBlockingIO(errStream.print(line.toString))
      def printLine(line: => Any)(using Trace): IO[IOException, Unit] =
        ZIO.attemptBlockingIO(outStream.println(line.toString))
      def printLineError(line: => Any)(using Trace): IO[IOException, Unit] =
        ZIO.attemptBlockingIO(errStream.println(line.toString))
      def readLine(using Trace): IO[IOException, String] =
        Console.ConsoleLive.readLine

  private def utf8(stream: OutputStream): PrintStream =
    PrintStream(stream, true, StandardCharsets.UTF_8)

  /** Standard input that isn't UTF-8, through `readAll`'s `Task`. */
  private final case class Undecodable(failure: ReadFailure)
      extends IOException(failure.describe("standard input"))
