package io.github.tsnee.ears.markdown

import scala.annotation.tailrec

/** A container a paragraph's first line opens, which each later line of the paragraph must match to
  * stay in it: a block quote, whose line needs a `>`, or a list item, whose line needs `width`
  * columns of indentation.
  */
private[markdown] enum Container derives CanEqual:
  case Quote
  case Item(width: Int)

/** A paragraph line as a table reads it: its `text` without its container prefix and its own
  * indentation; its `inner` text, without the container prefix but with its indentation; whether
  * it's lazy (it doesn't match all of its paragraph's containers, so a table ends there); and its
  * `indent`, the columns of whitespace past its container's content (four or more would be code).
  */
private[markdown] final case class RowLine(
    text: String,
    inner: String,
    isLazy: Boolean,
    indent: Int
) derives CanEqual

/** A table row's raw text, read as GitHub reads it before any inline formatting: its container
  * markers, its cells, whether it's a delimiter row, and whether it ends a table. Pipes are found
  * in the raw text, so a pipe in inline code or a link's destination separates cells too, and a
  * character reference for one (`&#124;`) never does. Whitespace in a line's prefix is a space or a
  * tab, as CommonMark has it; a tab moves to the next multiple of four columns.
  */
private[markdown] object Rows:

  private val delimiterCell = ":?-+:?".r

  /** The longest ordered list marker's number, as CommonMark allows it. */
  private val maxDigits = 9

  /** A list marker that starts a list item where a table row would be: a bullet, or one to nine
    * ASCII digits and `.` or `)`, followed by a space, a tab, or the line's end (an empty item).
    */
  private val listItem = "(?:[-+*]|[0-9]{1,9}[.)])(?:[ \t].*)?".r

  /** A thematic break, as CommonMark has it, past its indentation: three or more of the same `-`,
    * `*`, or `_`, with nothing else but spaces and tabs.
    */
  private val thematicBreak = "(?:-[ \t]*){3,}|(?:\\*[ \t]*){3,}|(?:_[ \t]*){3,}".r

  /** The other starts of a block that GitHub ends a table at, past their indentation: a block
    * quote's `>`; an ATX heading, one to six `#` followed by a space, a tab, or the line's end; and
    * a code fence, three or more backticks (with none in the rest of the line) or tildes. Each
    * interrupts a paragraph, so a table meets one only in the paragraph that joins it after an
    * underline row (`Tables`).
    */
  private val interruption = ">.*|#{1,6}(?:[ \t].*)?|`{3,}[^`]*|~{3,}.*".r

  /** The tag names of CommonMark's sixth kind of HTML block, as of CommonMark 0.31. */
  private val blockTags = List(
    "address",
    "article",
    "aside",
    "base",
    "basefont",
    "blockquote",
    "body",
    "caption",
    "center",
    "col",
    "colgroup",
    "dd",
    "details",
    "dialog",
    "dir",
    "div",
    "dl",
    "dt",
    "fieldset",
    "figcaption",
    "figure",
    "footer",
    "form",
    "frame",
    "frameset",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "head",
    "header",
    "hr",
    "html",
    "iframe",
    "legend",
    "li",
    "link",
    "main",
    "menu",
    "menuitem",
    "nav",
    "noframes",
    "ol",
    "optgroup",
    "option",
    "p",
    "param",
    "search",
    "section",
    "summary",
    "table",
    "tbody",
    "td",
    "tfoot",
    "th",
    "thead",
    "title",
    "tr",
    "track",
    "ul"
  ).mkString("|")

  /** The starts of CommonMark's seven kinds of HTML block, matched against a line in lowercase:
    * `<script`, `<pre`, `<style`, or `<textarea`; a comment; a processing instruction; a
    * declaration; CDATA; a block tag; and any other complete open or closing tag alone on its line.
    */
  private val htmlBlock = List(
    "<(?:script|pre|style|textarea)(?:[ \t>].*)?",
    "<!--.*",
    "<\\?.*",
    "<![a-z].*",
    "<!\\[cdata\\[.*",
    s"</?(?:$blockTags)(?:[ \t>].*|/>.*)?",
    "(?:<[a-z][a-z0-9-]*" +
      "(?:[ \t]+[a-z_:][a-z0-9_.:-]*(?:[ \t]*=[ \t]*(?:[^ \t\"'=<>`]+|'[^']*'|\"[^\"]*\"))?)*" +
      "[ \t]*/?>|</[a-z][a-z0-9-]*[ \t]*>)[ \t]*"
  ).map(_.r)

  /** Where a scan of a line stands: at the character `index`, which starts at column `start`, and
    * at column `column`, past `start` only inside a tab partly read as indentation.
    */
  private final case class Cursor(index: Int, start: Int, column: Int)

  /** A paragraph's line without its container prefix: leading whitespace and block-quote markers
    * (`>`), repeated; on the paragraph's `first` line also list markers (`-`, `+`, `*`, or one to
    * nine ASCII digits and `.` or `)`, each followed by a space or tab), repeated. No other line of
    * a paragraph can carry a list marker: a continuation line of a list item is indented, a lazy
    * one has none, and a marker there would have started a new block.
    */
  def stripped(line: String, first: Boolean): String =
    @tailrec
    def from(at: Int): Int =
      val start = line.indexWhere(char => !isBlank(char), at) match
        case -1    => line.length
        case found => found
      if line.startsWith(">", start) then from(start + 1)
      else
        (if first then listMarker(line, start) else None) match
          case Some(end) => from(end)
          case None      => start
    line.substring(from(0))

  /** The end of a list marker at `at`, if there's one there, followed by a space or tab. */
  private def listMarker(line: String, at: Int): Option[Int] =
    val digits = line.indexWhere(!isDigit(_), at) match
      case -1    => line.length - at
      case found => found - at
    val end =
      if digits == 0 then
        Option.when(
          line.startsWith("-", at) || line.startsWith("+", at) ||
            line.startsWith("*", at)
        )(at + 1)
      else
        Option.when(
          digits <= maxDigits && (line.startsWith(".", at + digits) ||
            line.startsWith(")", at + digits))
        )(at + digits + 1)
    end.filter(after => after < line.length && isBlank(line.charAt(after)))

  /** The containers a paragraph's first line opens, in order from the outside, and the line read as
    * a row. Indentation of four or more columns before a marker or the text belongs to a list item
    * opened on an earlier line, whose content is taken to start there; less is the line's own. A
    * list marker's item needs the marker's columns and the spaces after it (one, when there are
    * five or more) on each later line.
    */
  def opened(line: String): (List[Container], RowLine) =
    @tailrec
    def open(at: Cursor, done: List[Container]): (List[Container], Cursor) =
      val spaces = indentation(line, at)
      if spaces >= 4 then open(skip(line, at, spaces), Container.Item(spaces) :: done)
      else
        quote(line, at) match
          case Some(next) => open(next, Container.Quote :: done)
          case None       =>
            item(line, at) match
              case Some((next, width)) => open(next, Container.Item(width) :: done)
              case None                => (done.reverse, at)
    val (containers, content) = open(Cursor(0, 0, 0), Nil)
    containers -> row(line, content, isLazy = false)

  /** A later line of a paragraph whose first line opened `containers`, read as a row: lazy when it
    * doesn't match them all.
    */
  def matched(line: String, containers: List[Container]): RowLine =
    @tailrec
    def matching(at: Cursor, rest: List[Container]): RowLine = rest match
      case Nil                      => row(line, at, isLazy = false)
      case Container.Quote :: later =>
        quote(line, at) match
          case Some(next) => matching(next, later)
          case None       => row(line, at, isLazy = true)
      case Container.Item(width) :: later =>
        if indentation(line, at) >= width then matching(skip(line, at, width), later)
        else row(line, at, isLazy = true)
    matching(Cursor(0, 0, 0), containers)

  /** Whether GitHub ends a table at this line rather than read it as a body row: a lazy line, a
    * line indented four or more columns past its container's content (a code block), a thematic
    * break, a block quote, an ATX heading, a code fence, or one whose text starts a list item (an
    * empty one too) or an HTML block.
    */
  def endsTable(line: RowLine): Boolean =
    line.isLazy || line.indent >= 4 || thematicBreak.matches(line.text) ||
      interruption.matches(line.text) || startsBlock(line.text)

  /** Whether `text`, a line without its prefix and indentation, starts a list item or an HTML
    * block.
    */
  def startsBlock(text: String): Boolean =
    val lower = text.map(char => if char >= 'A' && char <= 'Z' then (char + 32).toChar else char)
    listItem.matches(text) || htmlBlock.exists(_.matches(lower))

  /** The row at `content`, past the containers matched: its indentation and the text after. */
  private def row(line: String, content: Cursor, isLazy: Boolean): RowLine =
    val spaces = indentation(line, content)
    RowLine(
      text = rest(line, skip(line, content, spaces)),
      inner = rest(line, content),
      isLazy = isLazy,
      indent = spaces
    )

  /** A block-quote marker at `at`, after at most three columns of indentation, and the one space or
    * tab column after it, if any.
    */
  private def quote(line: String, at: Cursor): Option[Cursor] =
    val spaces = indentation(line, at)
    val marker = skip(line, at, spaces)
    Option
      .when(spaces < 4 && line.startsWith(">", marker.index))(
        Cursor(marker.index + 1, marker.column + 1, marker.column + 1)
      )
      .map(next => skip(line, next, indentation(line, next).min(1)))

  /** A list item's marker at `at`, after at most three columns of indentation and followed by a
    * space or tab (a paragraph's first line has its text after it): the cursor at its content and
    * the columns from `at` to there, the marker's spaces after it counted only up to four.
    */
  private def item(line: String, at: Cursor): Option[(Cursor, Int)] =
    val spaces = indentation(line, at)
    val marker = skip(line, at, spaces)
    Option
      .when(spaces < 4)(listMarker(line, marker.index))
      .flatten
      .map { end =>
        val after = marker.column + end - marker.index
        val next = Cursor(end, after, after)
        val padding = indentation(line, next)
        val content = skip(line, next, if padding >= 5 then 1 else padding)
        content -> (content.column - at.column)
      }

  /** Columns of spaces and tabs from `at` to the next other character. */
  private def indentation(line: String, at: Cursor): Int =
    skip(line, at, Int.MaxValue).column - at.column

  /** Past at most `columns` columns of spaces and tabs from `at`, reading part of a tab if that's
    * where they end.
    */
  @tailrec
  private def skip(line: String, at: Cursor, columns: Int): Cursor =
    if columns <= 0 || at.index >= line.length || !isBlank(line.charAt(at.index)) then at
    else
      val end = at.start + (if line.charAt(at.index) == '\t' then 4 - at.start % 4 else 1)
      val available = end - at.column
      if available <= columns then skip(line, Cursor(at.index + 1, end, end), columns - available)
      else at.copy(column = at.column + columns)

  /** The line from `at`, the unread part of a tab as spaces. */
  private def rest(line: String, at: Cursor): String =
    if at.column > at.start && at.index < line.length then
      " " * (at.start + 4 - at.start % 4 - at.column) + line.substring(at.index + 1)
    else line.substring(at.index.min(line.length))

  private def isBlank(char: Char): Boolean = char == ' ' || char == '\t'

  private def isDigit(char: Char): Boolean = char >= '0' && char <= '9'

  /** The row's cells, trimmed of whitespace: split at each pipe with no backslash right before it,
    * and with one backslash removed before each pipe that has any. An empty first or last cell left
    * by an outer pipe is dropped.
    */
  def cells(row: String): List[String] = withoutOuter(parts(row)).map(trimmed)

  /** A delimiter row: a pipe that separates cells, and each cell only dashes, with an optional
    * colon at either end.
    */
  def isDelimiter(row: String): Boolean =
    val split = parts(row)
    split.size > 1 && withoutOuter(split).map(trimmed).forall(delimiterCell.matches)

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
