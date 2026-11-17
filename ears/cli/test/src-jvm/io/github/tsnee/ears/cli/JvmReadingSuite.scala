package io.github.tsnee.ears.cli

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

import zio.*
import zio.test.*

/** The JVM's `Reading`: real files and streams, read as bytes, at most as many as asked for. */
object JvmReadingSuite extends ZIOSpecDefault:

  private val noStdin: Task[Nothing] = ZIO.fail(IllegalStateException("stdin not expected"))

  /** The default input size limit, 10 MiB. */
  private val limit = 10485760

  private def temporary(make: => Path): Task[Path] =
    ZIO.attemptBlocking {
      val path = make
      path.toFile.deleteOnExit()
      path
    }

  /** The JVM's error reading `path`, or None when it was read. */
  private def failure(path: Path): UIO[Option[String]] =
    Main.reading(_ => noStdin).file(path.toString, limit).fold(Some(_), _ => None)

  def spec = suite("JVM reading")(
    test("a file is read as its bytes"):
      val text = "The scheduler shall list upcoming events — daily.\n"
      for
        path <- temporary(Files.writeString(Files.createTempFile("ears-check", ".txt"), text))
        read <- Main.reading(_ => noStdin).file(path.toString, limit).mapError(Exception(_))
      yield assertTrue(read.toList == text.getBytes(StandardCharsets.UTF_8).toList)
    ,
    test("a missing file"):
      for
        directory <- temporary(Files.createTempDirectory("ears-check"))
        found <- failure(directory.resolve("missing.txt"))
      yield assertTrue(found.exists(_.contains("NoSuchFileException")))
    ,
    test("a directory"):
      for
        directory <- temporary(Files.createTempDirectory("ears-check"))
        found <- failure(directory)
      yield assertTrue(found.isDefined)
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
      yield assertTrue(found.exists(_.contains("AccessDeniedException")))
    // Root reads any file whatever its mode.
    @@ TestAspect.ifProp("user.name")(_ != "root"),
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
    test("standard input is read as its bytes"):
      val bytes =
        "The scheduler shall list upcoming events — daily.\n".getBytes(StandardCharsets.UTF_8)
      Main
        .reading(Main.readUpTo(ByteArrayInputStream(bytes)))
        .standardInput(limit)
        .mapError(Exception(_))
        .map(read => assertTrue(read.toList == bytes.toList))
    ,
    test("standard input that fails: the stream's error, for the log"):
      for
        stream <- ZIO.succeed {
          val closed = BufferedInputStream(ByteArrayInputStream(Array.emptyByteArray))
          closed.close()
          closed
        }
        found <- Main
          .reading(Main.readUpTo(stream))
          .standardInput(limit)
          .fold(Some(_), _ => None)
      yield assertTrue(found.exists(_.contains("Stream closed")))
    ,
    test("a standard input that never ends: no more than asked for is read"):
      val endless = new java.io.InputStream:
        def read(): Int = 'y'.toInt
      Main
        .reading(Main.readUpTo(endless))
        .standardInput(1001)
        .mapError(Exception(_))
        .map(read => assertTrue(read.length == 1001))
    ,
    test("a directory's entries and their kinds, with no link followed"):
      for
        root <- temporary(Files.createTempDirectory("ears-check"))
        scheduling <- temporary(Files.writeString(root.resolve("scheduling.md"), "x"))
        _ <- temporary(Files.createDirectory(root.resolve("billing")))
        _ <- temporary(Files.writeString(root.resolve(".draft.md"), "x"))
        _ <- temporary(Files.createSymbolicLink(root.resolve("keys.md"), scheduling))
        _ <- temporary(Files.createSymbolicLink(root.resolve("loop"), root))
        _ <- temporary(Files.createSymbolicLink(root.resolve("old.md"), root.resolve("none.md")))
        entries <- Main.reading(_ => noStdin).entries(root.toString).mapError(Exception(_))
      yield assertTrue(
        entries.sortBy(_.name) == List(
          Entry(".draft.md", EntryKind.File),
          Entry("billing", EntryKind.Directory),
          Entry("keys.md", EntryKind.Link),
          Entry("loop", EntryKind.Link),
          Entry("old.md", EntryKind.Link),
          Entry("scheduling.md", EntryKind.File)
        )
      )
    ,
    test("a directory the user may not list: the JVM's error, for the log"):
      for
        locked <- temporary(
          Files.createTempDirectory(
            "ears-check",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("---------"))
          )
        )
        found <- Main.reading(_ => noStdin).entries(locked.toString).fold(Some(_), _ => None)
      yield assertTrue(found.exists(_.contains("AccessDeniedException")))
    // Root lists any directory whatever its mode.
    @@ TestAspect.ifProp("user.name")(_ != "root"),
    test("an entry that's neither a file, a directory, nor a link: other"):
      for
        root <- temporary(Files.createTempDirectory("ears-check"))
        socket = root.resolve("feed.md")
        entries <- ZIO.scoped(
          ZIO
            .fromAutoCloseable(
              ZIO.attemptBlocking(ServerSocketChannel.open(StandardProtocolFamily.UNIX))
            )
            .tap(channel => ZIO.attemptBlocking(channel.bind(UnixDomainSocketAddress.of(socket))))
            .zipRight(Main.reading(_ => noStdin).entries(root.toString).mapError(Exception(_)))
        )
        _ <- ZIO.attemptBlocking(Files.deleteIfExists(socket))
      yield assertTrue(entries == List(Entry("feed.md", EntryKind.Other)))
    ,
    test("an entry whose kind can't be read is a file, so reading it reports it"):
      for
        root <- temporary(Files.createTempDirectory("ears-check"))
        sub <- temporary(Files.createDirectory(root.resolve("sub")))
        _ <- temporary(Files.writeString(sub.resolve("scheduling.md"), "x"))
        // Listable but not searchable: the names can be read, their attributes can't.
        _ <- ZIO.attemptBlocking(
          Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("r--------"))
        )
        entries <- Main.reading(_ => noStdin).entries(sub.toString).mapError(Exception(_))
        _ <- ZIO.attemptBlocking(
          Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("rwx------"))
        )
      yield assertTrue(entries == List(Entry("scheduling.md", EntryKind.File)))
    // Root reads any entry's attributes whatever the directory's mode.
    @@ TestAspect.ifProp("user.name")(_ != "root"),
    test("a path given is a directory, through a link too, and nothing else is"):
      val reading = Main.reading(_ => noStdin)
      for
        root <- temporary(Files.createTempDirectory("ears-check"))
        file <- temporary(Files.writeString(root.resolve("scheduling.md"), "x"))
        link <- temporary(Files.createSymbolicLink(root.resolve("current"), root))
        directory <- reading.isDirectory(root.toString)
        linked <- reading.isDirectory(link.toString)
        notFile <- reading.isDirectory(file.toString)
        notMissing <- reading.isDirectory(root.resolve("missing").toString)
      yield assertTrue(directory, linked, !notFile, !notMissing)
    ,
    test("a file: no more than asked for is read"):
      for
        path <- temporary(Files.writeString(Files.createTempFile("ears-check", ".txt"), "abcd"))
        read <- Main.reading(_ => noStdin).file(path.toString, 3).mapError(Exception(_))
      yield assertTrue(read.toList == "abc".getBytes(StandardCharsets.UTF_8).toList)
  )
