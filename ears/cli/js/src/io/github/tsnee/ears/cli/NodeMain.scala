package io.github.tsnee.ears.cli

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.annotation.JSName
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

  /** Files by path, and standard input (file descriptor 0), each read whole and decoded strictly as
    * UTF-8 (see `Utf8`). Not with Node's "utf8" encoding, which replaces invalid bytes.
    */
  val reading: Reading =
    Reading(
      name => decoded(ZIO.attempt(NodeFs.readFileSync(name))),
      decoded(ZIO.attempt(NodeFs.readDescriptorSync(0)))
    )

  private def decoded(read: Task[Uint8Array]): IO[(ReadFailure, String), String] =
    failingAs(read).flatMap(buffer =>
      val bytes = int8Array2ByteArray(Int8Array(buffer.buffer, buffer.byteOffset, buffer.length))
      ZIO.fromEither(Utf8.decode(bytes)).mapError(failure => failure -> failure.decodingError)
    )

  /** Any error, uninterpreted (see `ReadFailure`), described whole for the log: a JavaScript
    * exception's description holds Node's message, which starts with its code.
    */
  private def failingAs[A](read: Task[A]): IO[(ReadFailure, String), A] =
    read.mapError(error => ReadFailure.Unreadable -> error.toString)

/** What the CLI uses of `node:fs`. */
@js.native
@JSImport("node:fs", JSImport.Namespace)
private object NodeFs extends js.Object:
  /** The file's bytes, as a Node Buffer (a Uint8Array). */
  def readFileSync(path: String): Uint8Array = js.native
  @JSName("readFileSync")
  def readDescriptorSync(descriptor: Int): Uint8Array = js.native

/** What the CLI uses of `node:process`. */
@js.native
@JSImport("node:process", JSImport.Default)
private object NodeProcess extends js.Object:
  val argv: js.Array[String] = js.native
