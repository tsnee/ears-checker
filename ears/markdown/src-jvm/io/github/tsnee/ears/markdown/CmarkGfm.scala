package io.github.tsnee.ears.markdown

import java.nio.charset.StandardCharsets.ISO_8859_1
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.Using

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import com.dylibso.chicory.wasm.types.FunctionImport

/** The C entry point (`ears/markdown/c`) on the JVM: cmark-gfm's WebAssembly module, compiled to
  * JVM classes when the checker is built (`CmarkGfmModuleMachine`, with the module's `.meta` beside
  * it) and run by Chicory. Each call gets an instance of its own, so no memory is shared between
  * calls or threads, and none is freed: the instance is dropped whole.
  */
private[markdown] object CmarkGfm:

  /** The module's description, without its code, which is in the generated classes; read once. */
  private lazy val module: Either[String, WasmModule] =
    Option(classOf[CmarkGfmModuleMachine].getResourceAsStream("CmarkGfmModule.meta"))
      .toRight("CmarkGfmModule.meta isn't on the class path")
      .flatMap(stream => attempt(Using.resource(stream)(Parser.parse)))

  /** cmark-gfm's release, such as `0.29.0.gfm.13`. */
  def version(): Either[String, String] =
    instance().flatMap(running =>
      attempt {
        val pointer = running.`export`("ears_version").apply()(0).toInt
        running.memory().readCString(pointer, UTF_8)
      }
    )

  /** The entry point's records of a document, or why there are none. */
  def records(content: String): Either[String, IArray[Byte]] =
    val bytes = content.getBytes(UTF_8)
    for
      running <- instance()
      memory = running.memory()
      // One byte more than the text, so an empty document has a buffer too.
      text <- attempt(running.`export`("ears_alloc").apply(bytes.length + 1L)(0).toInt)
        .flatMap(CmarkCall.allocated)
      _ <- attempt(memory.write(text, bytes))
      tree <- attempt(
        running.`export`("ears_tree").apply(text.toLong, bytes.length.toLong)(0).toInt
      )
        .flatMap(CmarkCall.allocated)
      // Up to the NUL the records end with, which is never in them: read as ISO-8859-1, one
      // character per byte, so the bytes come back unchanged. Chicory throws if memory ends first.
      records <- attempt(memory.readCString(tree, ISO_8859_1).getBytes(ISO_8859_1))
    yield IArray.unsafeFromArray(records)

  /** A new instance of the module, initialized. Its imports from WASI are never called (the entry
    * point does no I/O and reads no clock), so each is a stub returning ENOSYS (52).
    */
  private[markdown] def instance(): Either[String, Instance] =
    module.flatMap(wasm =>
      attempt {
        val stubs = wasm
          .importSection()
          .stream()
          .iterator()
          .asScala
          .collect { case function: FunctionImport =>
            HostFunction(
              function.module(),
              function.name(),
              wasm.typeSection().getType(function.typeIndex()),
              enosys
            )
          }
          .toList
        val running = Instance
          .builder(wasm)
          .withImportValues(ImportValues.builder().withFunctions(stubs.asJava).build())
          .withMachineFactory(CmarkGfmModuleMachine(_))
          .build()
        val _ = running.`export`("_initialize").apply()
        running
      }
    )

  /** A WASI function that fails with ENOSYS; a class, since the handle's parameters are varargs,
    * which a lambda can't implement.
    */
  private[markdown] object enosys extends WasmFunctionHandle:
    def apply(running: Instance | Null, arguments: Long*): Array[Long] = Array(52L)

  /** A call into Chicory, with what it threw (a trap, or a module that doesn't load) as the
    * failure.
    */
  private def attempt[A](call: => A): Either[String, A] =
    Try(call).toEither.left.map(thrown => s"cmark-gfm failed: $thrown")
