package io.github.tsnee.ears.markdown

/** What the platforms' calls of the C entry point (`CmarkGfm`) decide from its results, the same on
  * every platform that addresses its memory with numbers (the WebAssembly module's, on the JVM and
  * Node).
  */
private[markdown] object CmarkCall:

  /** A pointer `ears_alloc` or `ears_tree` returned, or the failure its `NULL` means. */
  def allocated(pointer: Int): Either[String, Int] =
    if pointer == 0 then Left(noResult) else Right(pointer)

  /** The offset of the NUL that ends the records, as a search found it (-1 for none), or the
    * failure a missing one means.
    */
  def terminated(nul: Int): Either[String, Int] =
    if nul < 0 then Left("cmark-gfm's records have no NUL ending them") else Right(nul)

  /** Why `ears_alloc` or `ears_tree` returned `NULL`: memory ran out, or (`ears_tree` only) the
    * parser, its table extension, or their one-time registration couldn't be set up.
    */
  val noResult: String =
    "cmark-gfm returned nothing: it ran out of memory, or its parser couldn't start"
