package io.github.tsnee.ears.releasenotes

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import zio.*

/** `ears.releaseNotes.run check <changelog>` checks the changelog's form;
  * `ears.releaseNotes.run notes <version> <changelog> [<files>]` writes the version's release notes
  * to standard output: its section, then the files description with `{version}` replaced. Exits 1,
  * with the problem on standard error, when the changelog doesn't fit or a file can't be read; 2
  * for bad arguments. Mill's `run` exits 1 whenever the program fails, so through Mill both are 1.
  */
object Main extends ZIOAppDefault:

  // The only logger: ZIO's format, on standard error, so standard output carries only the result.
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers ++
      Runtime.addLogger(ZLogger.default.map(line => java.lang.System.err.println(line)))

  def run: ZIO[ZIOAppArgs, Nothing, Unit] =
    getArgs.flatMap(arguments => cli(arguments.toList)).flatMap(exit)

  private val usage =
    "Usage: ears.releaseNotes.run check <changelog>\n" +
      "       ears.releaseNotes.run notes <version> <changelog> [<files>]"

  /** Everything but the process: the exit code, with the outcome on the console. */
  def cli(arguments: List[String]): UIO[ExitCode] = arguments match
    case List("check", changelog) => check(Paths.get(changelog))
    case "notes" :: version :: changelog :: files if files.sizeIs <= 1 =>
      Version.parse(version) match
        case Some(v) => notes(v, Paths.get(changelog), files.headOption.map(Paths.get(_)))
        case None    => badArguments(arguments, s"'$version' isn't <major>.<minor>.<patch>")
    case _ => badArguments(arguments, "Unrecognized command")

  private def check(changelog: Path): UIO[ExitCode] =
    read(changelog).flatMap {
      case None       => ZIO.succeed(ExitCode.failure)
      case Some(text) =>
        Changelog.sections(text) match
          case Left(problem)   => rejected(changelog, problem)
          case Right(sections) =>
            val releases = sections.size - 1
            val summary = s"$changelog has the changelog's form: Unreleased and $releases " +
              (if releases == 1 then "release" else "releases")
            ZIO.logInfo(summary) *> Console.printLine(summary).orDie.as(ExitCode.success)
    }

  private def notes(version: Version, changelog: Path, files: Option[Path]): UIO[ExitCode] =
    read(changelog).flatMap {
      case None       => ZIO.succeed(ExitCode.failure)
      case Some(text) =>
        ZIO.foreach(files)(read).flatMap {
          case Some(None) => ZIO.succeed(ExitCode.failure)
          case described  =>
            Changelog.notes(text, version, described.flatten) match
              case Left(problem) => rejected(changelog, problem)
              case Right(notes)  =>
                ZIO.logInfo(
                  s"Wrote the notes for ${version.show} from $changelog" +
                    files.fold("")(f => s" and $f") + s": ${notes.length} characters"
                ) *> Console.print(notes).orDie.as(ExitCode.success)
        }
    }

  /** The file's text, or `None` once the failure is logged and said on standard error. */
  private def read(file: Path): UIO[Option[String]] =
    ZIO
      .attemptBlocking(Files.readString(file))
      .foldZIO(
        error =>
          val what = s"Cannot read $file"
          ZIO.logErrorCause(what, Cause.fail(error)) *>
            Console.printLineError(s"$what: ${error.getMessage}").orDie.as(None)
        ,
        text => ZIO.logInfo(s"Read $file: ${text.length} characters").as(Some(text))
      )

  private def rejected(changelog: Path, problem: Problem): UIO[ExitCode] =
    val what = s"$changelog: ${problem.message}"
    ZIO.logError(what) *> Console.printLineError(what).orDie.as(ExitCode.failure)

  private def badArguments(arguments: List[String], why: String): UIO[ExitCode] =
    ZIO.logError(s"Bad arguments: ${arguments.mkString(" ")}: $why") *>
      Console.printLineError(s"$why\n$usage").orDie.as(ExitCode(2))
