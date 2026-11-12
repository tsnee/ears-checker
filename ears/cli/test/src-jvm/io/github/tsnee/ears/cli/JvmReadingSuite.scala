package io.github.tsnee.ears.cli

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

import zio.*
import zio.test.*

/** The JVM's `Reading`: real files and streams, read as bytes and decoded strictly as UTF-8. */
object JvmReadingSuite extends ZIOSpecDefault:

  private val noStdin: Task[String] = ZIO.fail(IllegalStateException("stdin not expected"))

  /** "ab", then "l\xe9vent": é in Latin-1, not valid UTF-8, at line 2, column 2. */
  private val latin1: Array[Byte] =
    "ab\nl".getBytes(StandardCharsets.US_ASCII) ++ Array(0xe9.toByte) ++
      "vent\n".getBytes(StandardCharsets.US_ASCII)

  private def temporary(make: => Path): Task[Path] =
    ZIO.attemptBlocking {
      val path = make
      path.toFile.deleteOnExit()
      path
    }

  /** The failure reading `path` gives, or None when it was read. */
  private def failure(path: Path): UIO[Option[ReadFailure]] =
    Main.reading(noStdin).file(path.toString).fold(failed => Some(failed._1), _ => None)

  def spec = suite("JVM reading")(
    test("a file is read as UTF-8"):
      val text = "The scheduler shall list upcoming events — daily.\n"
      for
        path <- temporary(Files.writeString(Files.createTempFile("ears-check", ".txt"), text))
        read <- Main.reading(noStdin).file(path.toString).mapError(failed => Exception(failed._2))
      yield assertTrue(read == text)
    ,
    test("a missing file"):
      for
        directory <- temporary(Files.createTempDirectory("ears-check"))
        found <- failure(directory.resolve("missing.txt"))
      yield assertTrue(found.contains(ReadFailure.Unreadable))
    ,
    test("a directory"):
      for
        directory <- temporary(Files.createTempDirectory("ears-check"))
        found <- failure(directory)
      yield assertTrue(found.contains(ReadFailure.Unreadable))
    ,
    test("a file the user may not read"):
      for
        path <- temporary(
          Files.createTempFile(
            "ears-check",
            ".txt",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("-w-------"))
          )
        )
        found <- failure(path)
      yield assertTrue(found.contains(ReadFailure.Unreadable))
    // Root reads any file whatever its mode.
    @@ TestAspect.ifProp("user.name")(_ != "root"),
    test("invalid UTF-8 in a file: its position"):
      for
        path <- temporary(
          Files.write(Files.createTempFile("ears-check", ".txt"), latin1)
        )
        found <- failure(path)
      yield assertTrue(found.contains(ReadFailure.NotUtf8(2, 2)))
    ,
    test("invalid UTF-8 on standard input: its position"):
      Main
        .reading(Main.readAll(ByteArrayInputStream(latin1)))
        .standardInput
        .fold(failed => Some(failed._1), _ => None)
        .map(found => assertTrue(found.contains(ReadFailure.NotUtf8(2, 2))))
    ,
    test("the JVM's log lines reach the given stream, one per line"):
      val err = ByteArrayOutputStream()
      ZIO
        .scoped(
          (ZLayer.succeed(ZIOAppArgs(Chunk.empty)) >>> Main.bootstrapTo(err)).build *>
            ZIO.logError("é")
        )
        .as(
          assertTrue(err.toByteArray.toList.takeRight(3) == List(0xc3, 0xa9, '\n').map(_.toByte))
        )
    ,
    // Since JDK 18 the default charset is UTF-8, so this can't tell the explicit one from the
    // default; scripts/cli-cases' c-locale case is what holds the jar to UTF-8 under the C locale.
    test("the console's two streams carry each line, out and err apart"):
      val out = ByteArrayOutputStream()
      val err = ByteArrayOutputStream()
      for _ <- ZIO.withConsole(Main.utf8Console(out, err))(
          Console.print("é") *> Console.printError("ü") *> Console.printLine("") *>
            Console.printLineError("")
        )
      yield assertTrue(
        out.toByteArray.toList == List(0xc3, 0xa9, '\n').map(_.toByte),
        err.toByteArray.toList == List(0xc3, 0xbc, '\n').map(_.toByte)
      )
    ,
    test("standard input is read as UTF-8"):
      val text = "The scheduler shall list upcoming events — daily.\n"
      Main
        .reading(
          Main.readAll(ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
        )
        .standardInput
        .mapError(failed => Exception(failed._2))
        .map(read => assertTrue(read == text))
    ,
    test("standard input that fails: unreadable, with the stream's error for the log"):
      for
        stream <- ZIO.succeed {
          val closed = BufferedInputStream(ByteArrayInputStream(Array.emptyByteArray))
          closed.close()
          closed
        }
        found <- Main
          .reading(Main.readAll(stream))
          .standardInput
          .fold(Some(_), _ => None)
      yield assertTrue(
        found.map(_._1).contains(ReadFailure.Unreadable),
        found.exists(_._2.contains("Stream closed"))
      )
  )
