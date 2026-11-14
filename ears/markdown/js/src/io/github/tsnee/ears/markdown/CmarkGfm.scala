package io.github.tsnee.ears.markdown

import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.*
import scala.util.Try

/** cmark-gfm's WebAssembly module, as bytes, from `ears/markdown/js/cmark-gfm-wasm.mjs`. */
@js.native
@JSImport("cmark-gfm-wasm", "bytes")
private val cmarkGfmWasm: Uint8Array = js.native

/** A compiled WebAssembly module. (A native constructor's parameters go to JavaScript, which the
  * unused-parameter check can't see, hence the `@nowarn` here and on `WasmInstance`.)
  */
@js.native
@JSGlobal("WebAssembly.Module")
@nowarn("msg=unused explicit parameter")
private class WasmModule(bytes: Uint8Array) extends js.Object

@js.native
@JSGlobal("WebAssembly.Module")
private object WasmModule extends js.Object:
  def imports(module: WasmModule): js.Array[WasmImport] = js.native

/** One of a module's imports: the module it's from and its name. */
@js.native
private trait WasmImport extends js.Object:
  val module: String = js.native
  val name: String = js.native

/** An instance of a module, with its imports: functions of no arguments, by module and name. */
@js.native
@JSGlobal("WebAssembly.Instance")
@nowarn("msg=unused explicit parameter")
private class WasmInstance(
    module: WasmModule,
    imports: js.Dictionary[js.Dictionary[js.Function0[Int]]]
) extends js.Object:
  val exports: CmarkGfmExports = js.native

/** What the entry point's module exports (`ears/markdown/c/ears_cmark.c`), pointers as numbers. */
@js.native
private trait CmarkGfmExports extends js.Object:
  val memory: WasmMemory = js.native
  def _initialize(): Unit = js.native
  def ears_alloc(size: Int): Int = js.native
  def ears_tree(text: Int, length: Int): Int = js.native
  def ears_version(): Int = js.native

@js.native
private trait WasmMemory extends js.Object:
  val buffer: ArrayBuffer = js.native

/** An `Int8Array` with `indexOf`, which Scala.js's facade lacks. */
@js.native
@JSGlobal("Int8Array")
private class SearchableBytes(memory: ArrayBuffer) extends Int8Array(memory):
  def indexOf(value: Byte, from: Int): Int = js.native

/** The C entry point (`ears/markdown/c`) on Node: cmark-gfm's WebAssembly module, run by Node's own
  * `WebAssembly`. The module is compiled once; each call gets an instance of its own, so no memory
  * is shared between calls, and none is freed: the instance is dropped whole.
  */
private[markdown] object CmarkGfm:

  /** The compiled module; compiled once. */
  private lazy val module: Either[String, WasmModule] = attempt(WasmModule(cmarkGfmWasm))

  /** cmark-gfm's release, such as `0.29.0.gfm.13`. */
  def version(): Either[String, String] =
    instance()
      .flatMap(exports => bytesFrom(exports, exports.ears_version()))
      .map(String(_, UTF_8))

  /** The entry point's records of a document, or why there are none. */
  def records(content: String): Either[String, IArray[Byte]] =
    val bytes = content.getBytes(UTF_8)
    for
      exports <- instance()
      // One byte more than the text, so an empty document has a buffer too.
      text <- attempt(exports.ears_alloc(bytes.length + 1)).flatMap(CmarkCall.allocated)
      _ <- attempt(Int8Array(exports.memory.buffer, text, bytes.length).set(bytes.toTypedArray))
      tree <- attempt(exports.ears_tree(text, bytes.length)).flatMap(CmarkCall.allocated)
      records <- bytesFrom(exports, tree)
    yield IArray.unsafeFromArray(records)

  /** The bytes of memory from `pointer` up to the next NUL, or the failure a missing one means. The
    * memory's buffer is read again for each call, since a call that grows the memory replaces it.
    */
  private def bytesFrom(exports: CmarkGfmExports, pointer: Int): Either[String, Array[Byte]] =
    for
      memory <- attempt(SearchableBytes(exports.memory.buffer))
      nul <- attempt(memory.indexOf(0, pointer)).flatMap(CmarkCall.terminated)
      bytes <- attempt(memory.subarray(pointer, nul).toArray)
    yield bytes

  /** A new instance of the module, initialized, as its exports. Its imports from WASI are never
    * called (the entry point does no I/O and reads no clock), so each is a stub returning ENOSYS
    * (52).
    */
  private def instance(): Either[String, CmarkGfmExports] =
    module.flatMap(compiled =>
      attempt {
        val stub: js.Function0[Int] = () => 52
        val imports = WasmModule
          .imports(compiled)
          .toList
          .groupMap(_.module)(_.name)
          .map((from, names) => from -> js.Dictionary(names.map(_ -> stub)*))
        val exports = WasmInstance(compiled, js.Dictionary(imports.toSeq*)).exports
        exports._initialize()
        exports
      }
    )

  /** A call into WebAssembly, with what it threw (a trap, or a module that doesn't load) as the
    * failure.
    */
  private def attempt[A](call: => A): Either[String, A] =
    Try(call).toEither.left.map(thrown => s"cmark-gfm failed: $thrown")
