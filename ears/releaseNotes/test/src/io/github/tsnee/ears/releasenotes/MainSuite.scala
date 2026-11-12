package io.github.tsnee.ears.releasenotes

import java.nio.file.Files
import java.nio.file.Path

import zio.*
import zio.test.*

/** The tool end to end: files in a temporary directory, the exit code, and what the console shows.
  */
object MainSuite extends ZIOSpecDefault:

  private val changelog =
    "# Changelog\n\n## Unreleased\n\n- Next.\n\n## 0.1.0 - 2026-11-03\n\n- The first release.\n"

  /** A new temporary directory holding `files`, by name. */
  private def directory(files: (String, String)*): Task[Path] =
    ZIO.attemptBlocking {
      val dir = Files.createTempDirectory("release-notes")
      files.foreach((name, text) => Files.writeString(dir.resolve(name), text))
      dir
    }

  /** Runs the tool and returns what it wrote, then clears both streams, so each run sees only its
    * own output. `TestConsole.clearOutput` clears only standard output, and the companion has no
    * accessor for the service's `clearOutputErr`, hence `testConsoleWith`.
    */
  private def run(arguments: String*): UIO[(Int, String, String)] =
    for
      code <- Main.cli(arguments.toList)
      out <- TestConsole.output
      err <- TestConsole.outputErr
      _ <- TestConsole.clearOutput *> testConsoleWith(_.clearOutputErr)
    yield (code.code, out.mkString, err.mkString)

  def spec = suite("release-notes tool")(
    test("check: a changelog with the form exits 0 and says what it found"):
      for
        dir <- directory("CHANGELOG.md" -> changelog)
        (code, out, err) <- run("check", dir.resolve("CHANGELOG.md").toString)
      yield assertTrue(code == 0, out.contains("Unreleased and 1 release\n"), err.isEmpty)
    ,
    test("check: the summary counts several releases"):
      val two = changelog + "\n## 0.0.1 - 2026-11-01\n\n- A preview.\n"
      for
        dir <- directory("CHANGELOG.md" -> two)
        (code, out, _) <- run("check", dir.resolve("CHANGELOG.md").toString)
      yield assertTrue(code == 0, out.contains("Unreleased and 2 releases\n"))
    ,
    test("check: a misnamed heading exits 1 and names it on standard error"):
      for
        dir <- directory("CHANGELOG.md" -> changelog.replace("## Unreleased", "## Next"))
        (code, out, err) <- run("check", dir.resolve("CHANGELOG.md").toString)
      yield assertTrue(code == 1, out.isEmpty, err.contains("Next"))
    ,
    test("notes: the section and the files description on standard output"):
      for
        dir <- directory(
          "CHANGELOG.md" -> changelog,
          "files.md" -> "## Files\n\n- `ears-check-{version}.jar`\n"
        )
        (code, out, err) <- run(
          "notes",
          "0.1.0",
          dir.resolve("CHANGELOG.md").toString,
          dir.resolve("files.md").toString
        )
      yield assertTrue(
        code == 0,
        out == "- The first release.\n\n## Files\n\n- `ears-check-0.1.0.jar`\n",
        err.isEmpty
      )
    ,
    test("notes: without a files description, only the section"):
      for
        dir <- directory("CHANGELOG.md" -> changelog)
        (code, out, _) <- run("notes", "0.1.0", dir.resolve("CHANGELOG.md").toString)
      yield assertTrue(code == 0, out == "- The first release.\n")
    ,
    test("notes: a version with no section exits 1 and names it"):
      for
        dir <- directory("CHANGELOG.md" -> changelog)
        (code, out, err) <- run("notes", "0.2.0", dir.resolve("CHANGELOG.md").toString)
      yield assertTrue(code == 1, out.isEmpty, err.contains("0.2.0"), err.contains("Unreleased"))
    ,
    test("a file that can't be read exits 1 and names it"):
      for
        dir <- directory()
        missing = dir.resolve("CHANGELOG.md").toString
        (checkCode, _, checkErr) <- run("check", missing)
        (notesCode, _, notesErr) <- run("notes", "0.1.0", missing)
        files = dir.resolve("files.md").toString
        _ <- ZIO.attemptBlocking(Files.writeString(dir.resolve("CHANGELOG.md"), changelog))
        (filesCode, _, filesErr) <- run("notes", "0.1.0", missing, files)
      yield assertTrue(
        checkCode == 1,
        checkErr.contains(s"Cannot read $missing"),
        notesCode == 1,
        notesErr.contains(s"Cannot read $missing"),
        filesCode == 1,
        filesErr.contains(s"Cannot read $files")
      )
    ,
    test("bad arguments exit 2 with the usage"):
      for results <- ZIO.foreach(
          List(
            Nil,
            List("check"),
            List("check", "a", "b"),
            List("notes", "0.1.0"),
            List("notes", "v0.1.0", "CHANGELOG.md"),
            List("notes", "0.1.0", "CHANGELOG.md", "files.md", "extra"),
            List("publish", "CHANGELOG.md")
          )
        )(arguments => run(arguments*))
      yield assertTrue(results.forall((code, _, err) => code == 2 && err.contains("Usage")))
  )
