package io.github.tsnee.ears.cli

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Int8Array
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.typedarray.int8Array2ByteArray

import zio.*

/** Node's entry point. Scala.js passes no arguments to `main`, so they come from `process.argv`,
  * after the paths of `node` and the script.
  */
object NodeMain:
  def main(arguments: Array[String]): Unit = NodeApp.main(NodeProcess.argv.toArray.drop(2))

/** `Cli` with files and standard input read through `node:fs`. */
object NodeApp extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Cli.bootstrap

  def run: ZIO[ZIOAppArgs, Nothing, Unit] = Cli.run(reading).flatMap(exit)

  /** Files by path, and standard input (file descriptor 0), at most a given number of bytes of
    * each, as bytes (not with Node's "utf8" encoding, which replaces invalid bytes). Any error is
    * uninterpreted (see `ReadFailure`) and described whole for the log: a JavaScript exception's
    * description holds Node's message, which starts with its code.
    */
  val reading: Reading =
    Reading(
      (name, most) =>
        ZIO
          .acquireReleaseWith(ZIO.attempt(NodeFs.openSync(name, "r")))(descriptor =>
            ZIO
              .attempt(NodeFs.closeSync(descriptor))
              .catchAll(error =>
                ZIO.logAnnotate("error", error.toString)(ZIO.logWarning("Cannot close input"))
              )
          )(upTo(_, most))
          .mapError(_.toString),
      most => upTo(0, most).mapError(_.toString)
    )

  /** At most `most` bytes of a descriptor, read in chunks of 64 KiB until there are `most` or the
    * input ends, so memory follows what the input holds rather than the limit.
    */
  private def upTo(descriptor: Int, most: Int): Task[Array[Byte]] =
    def from(read: Chunk[Byte]): Task[Chunk[Byte]] =
      if read.size >= most then ZIO.succeed(read)
      else
        chunk(descriptor, math.min(chunkSize, most - read.size)).flatMap(next =>
          if next.isEmpty then ZIO.succeed(read) else from(read ++ next)
        )
    from(Chunk.empty).map(_.toArray)

  private val chunkSize = 64 * 1024

  /** Up to `length` bytes, from the descriptor's current position (-1), as a stream is read; none
    * at the end. Windows ends a pipe with an `EOF` error where other systems return 0.
    */
  private def chunk(descriptor: Int, length: Int): Task[Chunk[Byte]] =
    ZIO
      .attempt {
        val buffer = Uint8Array(length)
        val read = NodeFs.readSync(descriptor, buffer, 0, length, -1)
        Chunk.fromArray(int8Array2ByteArray(Int8Array(buffer.buffer, 0, read)))
      }
      .catchSome {
        case error if Option(error.getMessage).exists(_.contains("EOF: end of file")) =>
          ZIO.succeed(Chunk.empty)
      }

/** What the CLI uses of `node:fs`. */
@js.native
@JSImport("node:fs", JSImport.Namespace)
private object NodeFs extends js.Object:
  /** A file descriptor for the path, opened with `flags` ("r" to read). */
  def openSync(path: String, flags: String): Int = js.native

  /** Reads up to `length` bytes into `buffer` from `offset`, at `position` (-1: the current one);
    * the number read, 0 at the end.
    */
  def readSync(descriptor: Int, buffer: Uint8Array, offset: Int, length: Int, position: Int): Int =
    js.native

  def closeSync(descriptor: Int): Unit = js.native

/** What the CLI uses of `node:process`. */
@js.native
@JSImport("node:process", JSImport.Default)
private object NodeProcess extends js.Object:
  val argv: js.Array[String] = js.native
