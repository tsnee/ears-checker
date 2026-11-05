package io.github.tsnee.ears.linkcheck

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.internal.semanticdb.TextDocuments

import zio.*

/** `ears.linkCheck.run <semanticdb directory> <output file>`: reads the library's SemanticDB,
  * writes the link-check program, and prints `LinkCheck.summary` for the operations it references.
  * Exits 1, naming each problem on standard error, when it can't write the program; 2 for bad
  * arguments.
  */
object Main extends ZIOAppDefault:

  // The only logger: ZIO's format, on standard error, so standard output carries only the result.
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers ++
      Runtime.addLogger(ZLogger.default.map(line => java.lang.System.err.println(line)))

  def run: ZIO[ZIOAppArgs, Nothing, Unit] =
    getArgs.flatMap(arguments => cli(arguments.toList)).flatMap(exit)

  /** Everything but the process: the exit code, with the outcome on the console. */
  def cli(arguments: List[String]): UIO[ExitCode] = arguments match
    case List(root, output) => generate(Paths.get(root), Paths.get(output))
    case _                  =>
      val usage = "Usage: ears.linkCheck.run <semanticdb directory> <output file>"
      ZIO.logError(s"Bad arguments: ${arguments.mkString(" ")}") *>
        Console.printLineError(usage).orDie.as(ExitCode(2))

  /** Every SemanticDB document under `root`, in file-name order. */
  def readDocuments(root: Path): Task[List[TextDocument]] =
    for
      files <- ZIO.acquireReleaseWith(ZIO.attemptBlocking(Files.walk(root)))(stream =>
        ZIO.succeed(stream.close())
      )(stream =>
        ZIO.attemptBlocking(
          stream.iterator.asScala
            .filter(_.toString.endsWith(".semanticdb"))
            .toList
            .sortBy(_.toString)
        )
      )
      documents <- ZIO.foreach(files)(file =>
        ZIO.attemptBlocking(TextDocuments.parseFrom(Files.readAllBytes(file)).documents.toList)
      )
    yield documents.flatten

  private def generate(root: Path, output: Path): UIO[ExitCode] =
    readDocuments(root).foldZIO(
      failed(s"Cannot read the SemanticDB under $root", _),
      documents =>
        ZIO.logInfo(s"Read ${documents.size} SemanticDB documents from $root") *>
          (LinkCheck.operations(documents) match
            case Left(problems) =>
              ZIO
                .foreachDiscard(problems)(problem =>
                  ZIO.logError(problem.message) *> Console.printLineError(problem.message).orDie
                )
                .as(ExitCode.failure)
            case Right(operations) => write(output, operations))
    )

  private def write(output: Path, operations: List[Operation]): UIO[ExitCode] =
    ZIO
      .attemptBlocking(Files.writeString(output, LinkCheck.program(operations)))
      .foldZIO(
        failed(s"Cannot write the link-check program to $output", _),
        _ =>
          ZIO.logInfo(s"Wrote $output, referencing ${operations.size} operations") *>
            Console.printLine(LinkCheck.summary(operations.size)).orDie.as(ExitCode.success)
      )

  /** Logs `what` with the error as its cause, says it on standard error, and exits 1. */
  private def failed(what: String, error: Throwable): UIO[ExitCode] =
    ZIO.logErrorCause(what, Cause.fail(error)) *>
      Console.printLineError(s"$what: ${error.getMessage}").orDie.as(ExitCode.failure)
