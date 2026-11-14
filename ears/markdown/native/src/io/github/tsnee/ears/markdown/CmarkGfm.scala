package io.github.tsnee.ears.markdown

import java.nio.charset.StandardCharsets.UTF_8
import scala.scalanative.libc.string
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** The C entry point (`ears/markdown/c/ears_cmark.c`), compiled into the binary with cmark-gfm. */
@extern
private object CmarkGfmEntry:
  def ears_tree(text: CString, length: CSize): CString = extern
  def ears_free(pointer: CString): Unit = extern
  def ears_version(): CString = extern

/** The C entry point (`ears/markdown/c`) on Scala Native: cmark-gfm, compiled into the binary from
  * its C source, called directly.
  */
private[markdown] object CmarkGfm:

  /** cmark-gfm's release, such as `0.29.0.gfm.13`. */
  def version(): Either[String, String] =
    Right(fromCString(CmarkGfmEntry.ears_version(), UTF_8))

  /** The entry point's records of a document, or why there are none. */
  def records(content: String): Either[String, IArray[Byte]] =
    Zone.acquire { zone =>
      given Zone = zone
      // The text's UTF-8 bytes, with one byte more, so an empty document has a buffer too; the
      // length passed is explicit, so a NUL inside the text is read too.
      val bytes = content.getBytes(UTF_8)
      val text = alloc[Byte](bytes.length + 1)
      bytes.indices.foreach(i => text(i) = bytes(i))
      val tree = CmarkGfmEntry.ears_tree(text, bytes.length.toCSize)
      // Copied out before it's freed: the bytes up to the NUL the records end with.
      Option(tree).filter(_.toLong != 0L).toRight(CmarkCall.noResult).map { records =>
        val length = string.strlen(records).toInt
        val copied = Array.tabulate(length)(records(_))
        CmarkGfmEntry.ears_free(records)
        IArray.unsafeFromArray(copied)
      }
    }
