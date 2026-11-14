package io.github.tsnee.ears.markdown

/** A table row's raw text split into cells as GitHub splits it, before any inline formatting: for
  * counting a row's cells, which cmark-gfm's tree leaves out past the header's count. Pipes are
  * found in the raw text, so a pipe in inline code or a link's destination separates cells too, and
  * a character reference for one (`&#124;`) never does.
  */
private[markdown] object Rows:

  /** The row's cells, trimmed of whitespace: split at each pipe with no backslash right before it,
    * and with one backslash removed before each pipe that has any. An empty first or last cell left
    * by an outer pipe is dropped.
    */
  def cells(row: String): List[String] = withoutOuter(parts(row)).map(trimmed)

  /** The row, trimmed, split at its separating pipes, with escaped pipes kept and one backslash
    * before each removed.
    */
  private def parts(row: String): List[String] =
    // Right to left would hide the run of backslashes before each pipe, so this reads left to
    // right: the cell so far (reversed), the cells done (reversed), and the backslashes just read.
    val (cell, done, _) =
      trimmed(row).foldLeft((List.empty[Char], List.empty[String], 0)) {
        case ((cell, done, 0), '|')            => (Nil, cell.reverse.mkString :: done, 0)
        case ((cell, done, _), '|')            => ('|' :: cell.drop(1), done, 0)
        case ((cell, done, backslashes), '\\') => ('\\' :: cell, done, backslashes + 1)
        case ((cell, done, _), char)           => (char :: cell, done, 0)
      }
    (cell.reverse.mkString :: done).reverse

  /** The parts of a trimmed row without the empty first one a leading pipe leaves and the empty
    * last one a trailing pipe leaves.
    */
  private def withoutOuter(parts: List[String]): List[String] =
    val withoutFirst = parts match
      case "" :: rest if rest.nonEmpty => rest
      case _                           => parts
    withoutFirst.reverse match
      case "" :: rest if rest.nonEmpty => rest.reverse
      case _                           => withoutFirst

  /** The text without leading and trailing ASCII whitespace, as cmark-gfm trims a cell (its
    * `cmark_isspace`: space, tab, line feed, line tabulation, form feed, carriage return), and not
    * other Unicode whitespace, which is text in a cell on GitHub.
    */
  private def trimmed(text: String): String =
    text.dropWhile(isSpace).reverse.dropWhile(isSpace).reverse

  private def isSpace(char: Char): Boolean =
    char == ' ' || char == '\t' || char == '\n' || char == '\r' || char == '\f' || char.toInt == 11
