package io.github.tsnee.ears.cli

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import scala.jdk.CollectionConverters.*

import zio.*

/** The JVM's entry point: `Cli` with files and standard input read through `java.nio`, and standard
  * output and error written as UTF-8, as on Node. (The JVM's own streams use the locale's charset,
  * which turns é into ? under the C locale.)
  */
object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    bootstrapTo(FileOutputStream(FileDescriptor.err))

  /** `Cli`'s logger, writing each line to `err` as UTF-8. `err` is made when the layer is built. */
  def bootstrapTo(err: => OutputStream): ZLayer[ZIOAppArgs, Nothing, Unit] =
    ZLayer
      .fromZIO(ZIO.succeed(utf8(err)))
      .flatMap(stream => Cli.bootstrapWith(line => stream.get[PrintStream].println(line)))

  def run: ZIO[ZIOAppArgs, Nothing, Unit] =
    ZIO
      .succeed(
        utf8Console(FileOutputStream(FileDescriptor.out), FileOutputStream(FileDescriptor.err))
      )
      .flatMap(console => ZIO.withConsole(console)(Cli.run(reading(readUpTo(java.lang.System.in)))))
      .flatMap(exit)

  /** Files by path, and `standardInput` (see `readUpTo`), at most a given number of bytes of each;
    * whether a path is a directory, and a directory's entries. The JVM's exceptions aren't
    * interpreted (see `ReadFailure`): each is logged whole.
    */
  def reading(standardInput: Int => Task[Array[Byte]]): Reading =
    Reading(
      (name, most) =>
        ZIO
          .acquireReleaseWith(ZIO.attemptBlocking(Files.newInputStream(Paths.get(name))))(
            closing
          )(in => readUpTo(in)(most))
          .mapError(_.toString),
      most => standardInput(most).mapError(_.toString),
      name => ZIO.attemptBlocking(Files.isDirectory(Paths.get(name))).orElseSucceed(false),
      name =>
        ZIO
          .acquireReleaseWith(ZIO.attemptBlocking(Files.newDirectoryStream(Paths.get(name))))(
            closing
          )(stream => ZIO.attemptBlocking(stream.iterator.asScala.toList))
          .flatMap(ZIO.foreach(_)(entry))
          .mapError(_.toString)
    )

  /** An entry by its path, classified without following a link (see `EntryKind`). One whose
    * attributes can't be read (in a directory that can be listed but not searched) is taken as a
    * file, as Node takes it from the listing, so reading it reports it rather than skipping it.
    */
  private def entry(path: Path): UIO[Entry] =
    ZIO
      .attemptBlocking(
        Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
      )
      .fold(
        _ => EntryKind.File,
        attributes =>
          if attributes.isSymbolicLink then EntryKind.Link
          else if attributes.isDirectory then EntryKind.Directory
          else if attributes.isRegularFile then EntryKind.File
          else EntryKind.Other
      )
      .map(Entry(path.getFileName.toString, _))

  /** Closes what a read opened; a failure is logged as a warning, since what was read stands. */
  private def closing(resource: AutoCloseable): UIO[Unit] =
    ZIO
      .attemptBlocking(resource.close())
      .catchAll(error =>
        ZIO.logAnnotate("error", error.toString)(ZIO.logWarning("Cannot close input"))
      )

  /** At most `most` bytes of a stream: no more is read, so a stream that never ends stops there. */
  def readUpTo(in: InputStream): Int => Task[Array[Byte]] =
    most => ZIO.attemptBlocking(in.readNBytes(most))

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
