package io.github.tsnee.ears.markdown

import scala.annotation.tailrec

/** A paragraph's content as a parser that gives inline nodes no positions reports it
  * (commonmark.js, on Node), before each piece's line is found.
  */
private[markdown] enum SourcePiece derives CanEqual:
  /** Text or inline code, as the parser decoded it. */
  case Inline(text: String, code: Boolean)

  /** A soft or hard line break. */
  case Break

/** Lines for a paragraph whose parser gives only the paragraph's start line: each piece's line is
  * found by finding the piece in the paragraph's source, in order. The search reads the source left
  * to right from just past the last piece it found, and looks only as far as the end of the next
  * line: between two breaks, text sits on one line, moved at most by line endings the parser hides
  * (in a code span, a link's title, inline HTML), and a bounded search keeps a paragraph's time
  * linear. A piece's words must appear in order with whitespace between them, any whitespace
  * matching any (a code span's space is a line ending in the source); after a line ending, a block
  * quote's `>` markers are skipped too. A match can't start or end inside a word. In text, a
  * backslash before ASCII punctuation in the source matches the punctuation alone, as CommonMark
  * decodes it; in code, backslashes are literal. The piece's line is the start line plus the line
  * endings before the match. A piece whose words can't all be found there (text an entity changed,
  * say) is placed at its first word, if that's found; otherwise at the line where the search
  * stands, which doesn't move. A break is a space with no line, as on every platform; since each
  * break is a real line ending, the search moves past the next one, but never past the paragraph's
  * last.
  */
private[markdown] object SourceLines:

  /** Where the search stands: an offset in the source, and the line it's on. */
  private final case class Cursor(offset: Int, line: Int)

  /** The paragraph, starting on `startLine`, with each piece's line found in `source`, the
    * paragraph's lines of the document.
    */
  def paragraph(source: String, startLine: Int, pieces: List[SourcePiece]): Paragraph =
    val (_, placed) = pieces.foldLeft((Cursor(0, startLine), List.empty[Piece])) {
      case ((cursor, done), SourcePiece.Break) =>
        (pastLineEnding(source, cursor), Piece(" ", false, None) :: done)
      case ((cursor, done), SourcePiece.Inline(text, code)) =>
        locate(source, words(text), cursor.offset, code) match
          case Some((start, end)) =>
            val line = cursor.line + lineEndings(source, cursor.offset, start)
            (
              Cursor(end, line + lineEndings(source, start, end)),
              Piece(text, code, Some(line)) :: done
            )
          case None => (cursor, Piece(text, code, Some(cursor.line)) :: done)
    }
    Paragraph(startLine, placed.reverse)

  /** Adjacent text pieces joined into one where `same(k, j)` says the node keyed `j` is the next
    * sibling of the one keyed `k`, as commonmark-java joins sibling text nodes. Code, breaks, text
    * of different parents (a link's text and the text after the link), and text with a dropped node
    * between (inline HTML) stay apart. commonmark.js splits text at each escape, which would
    * otherwise hide an escaped backslash before a backtick from the facade.
    */
  def joined[K](pieces: List[(SourcePiece, K)], same: (K, K) => Boolean): List[SourcePiece] =
    pieces
      .foldRight(List.empty[(SourcePiece, K)]) {
        case ((SourcePiece.Inline(a, false), k), (SourcePiece.Inline(b, false), j) :: rest)
            if same(k, j) =>
          (SourcePiece.Inline(a + b, false), k) :: rest
        case (piece, rest) => piece :: rest
      }
      .map(_._1)

  /** The end of `word` matched at `at`, or `None`. In text (`code` false), a backslash before ASCII
    * punctuation matches the punctuation alone, tried first so `\\` matches one backslash.
    */
  def matched(source: String, at: Int, word: String, code: Boolean): Option[Int] =
    matchedFrom(source, at, word, 0, code)

  private def words(text: String): List[String] = text.split("\\s+").toList.filter(_.nonEmpty)

  /** The start and end of the first match of `found` from `from` to the end of the next line: all
    * its words if they match there, else its first word alone.
    */
  private def locate(
      source: String,
      found: List[String],
      from: Int,
      code: Boolean
  ): Option[(Int, Int)] =
    found match
      case Nil           => None
      case first :: rest =>
        val starts = occurrences(source, first, from, windowEnd(source, from), code)
        starts
          .collectFirst(
            Function.unlift((start, end) => following(source, end, rest, code).map(start -> _))
          )
          .orElse(starts.headOption)

  /** Where `word` starts, from `from` up to `until`, and ends, not inside a longer word, in order
    * (lazily).
    */
  private def occurrences(
      source: String,
      word: String,
      from: Int,
      until: Int,
      code: Boolean
  ): LazyList[(Int, Int)] =
    LazyList
      .range(from, until)
      .filter(at => wordStart(source, at, word))
      .flatMap(at => matched(source, at, word, code).filter(wordEnd(source, _, word)).map(at -> _))

  /** Just past the second line ending from `from`: the rest of its line and the next. */
  private def windowEnd(source: String, from: Int): Int =
    val first = skip(source, from, c => c != '\n' && c != '\r')
    skip(
      source,
      skip(source, first, c => c == '\n' || c == '\r').max(first),
      c => c != '\n' && c != '\r'
    )

  @tailrec
  private def matchedFrom(
      source: String,
      at: Int,
      word: String,
      i: Int,
      code: Boolean
  ): Option[Int] =
    if i == word.length then Some(at)
    else if at >= source.length then None
    else if !code && source.charAt(at) == '\\' && at + 1 < source.length &&
      escapable(word.charAt(i)) && source.charAt(at + 1) == word.charAt(i)
    then matchedFrom(source, at + 2, word, i + 1, code)
    else if source.charAt(at) == word.charAt(i) then matchedFrom(source, at + 1, word, i + 1, code)
    else None

  /** ASCII punctuation, which a backslash escapes in CommonMark. */
  private def escapable(c: Char): Boolean =
    c < 128 && "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".contains(c)

  /** The cursor just past the next line ending at or after it, unless that ending is the
    * paragraph's last (or there's none): then where it is.
    */
  private def pastLineEnding(source: String, cursor: Cursor): Cursor =
    val ending = skip(source, cursor.offset, c => c != '\n' && c != '\r')
    val after = if source.startsWith("\r\n", ending) then ending + 2 else ending + 1
    if after >= source.length then cursor
    else Cursor(after, cursor.line + lineEndings(source, cursor.offset, after))

  /** The end of `words` matched from `at`, each after whitespace, or `None`. */
  @tailrec
  private def following(
      source: String,
      at: Int,
      remaining: List[String],
      code: Boolean
  ): Option[Int] =
    remaining match
      case Nil          => Some(at)
      case word :: more =>
        val next = separator(source, at)
        matched(source, next, word, code).filter(end =>
          next > at && wordEnd(source, end, word)
        ) match
          case Some(end) => following(source, end, more, code)
          case None      => None

  /** Past the whitespace from `at`, and past block-quote markers after a line ending. */
  @tailrec
  private def separator(source: String, at: Int): Int =
    val spaces = skip(source, at, Character.isWhitespace)
    val crossed = source.substring(at, spaces).exists(c => c == '\n' || c == '\r')
    val markers =
      if crossed then skip(source, spaces, c => c == '>' || c == ' ' || c == '\t') else spaces
    if markers == at then at
    else if markers == spaces then spaces
    else separator(source, markers)

  @tailrec
  private def skip(source: String, at: Int, p: Char => Boolean): Int =
    if at < source.length && p(source.charAt(at)) then skip(source, at + 1, p) else at

  private def wordStart(source: String, at: Int, word: String): Boolean =
    !word.headOption.exists(Character.isLetterOrDigit) || at == 0 ||
      !Character.isLetterOrDigit(source.charAt(at - 1))

  private def wordEnd(source: String, end: Int, word: String): Boolean =
    !word.lastOption.exists(Character.isLetterOrDigit) || end >= source.length ||
      !Character.isLetterOrDigit(source.charAt(end))

  /** The line endings in `source` from `from` to `to`: `\r\n`, `\r`, and `\n` count once each. */
  private def lineEndings(source: String, from: Int, to: Int): Int =
    (from until to).count(i =>
      source.charAt(i) == '\n' || (source.charAt(i) == '\r' && (i + 1 >= source.length ||
        source.charAt(i + 1) != '\n'))
    )
