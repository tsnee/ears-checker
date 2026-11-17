package io.github.tsnee.ears.cli

import scala.collection.Searching.Found
import scala.collection.Searching.InsertionPoint

/** A requirement's text in OpenSpec's Markdown, and the 1-based line it starts on. */
final case class Statement(line: Int, text: String) derives CanEqual

/** Requirements in OpenSpec's Markdown (a spec, or a change's delta spec), read by the OpenSpec
  * CLI's own rules (2.0.0, `dist/core/parsers/`), so each requirement and its text are what
  * `openspec show --json` gives. Those rules are line-based, not CommonMark, which is why this
  * doesn't read through the Markdown facade: a header is any line of one to six number signs and a
  * space outside a fenced code block, wherever CommonMark would put it.
  *
  * OpenSpec's patterns are JavaScript's, so their whitespace is JavaScript's (`isSpace`), wider
  * than Java's `\s` and different from `Character.isWhitespace`; they're written here as scans of
  * characters rather than regular expressions, so they mean the same on the JVM, Node, and Scala
  * Native.
  */
private[cli] object OpenSpecMarkdown:

  private final case class Header(index: Int, level: Int, title: String)

  /** The delta sections whose requirements are checked: added and modified ones have statements;
    * removed ones have a reason, and renamed ones only names.
    */
  private val checkedSections = List("ADDED Requirements", "MODIFIED Requirements")

  /** Every requirement in the spec's Requirements section and in the delta's ADDED and MODIFIED
    * sections, in document order, each with its text (`statement`).
    */
  def statements(content: String): List[Statement] =
    val lines = normalized(content)
    val fenced = fenceMask(lines)
    val unfenced = lines.indices.toList.filterNot(fenced)
    val headers = unfenced.flatMap(i => header(i, lines(i)))
    // Where a requirement's text ends: `requirement-text.js`' `HEADER_LINE`, `^#{1,6}\s`, which
    // needs no title, so it's looser than a header.
    val textEnds = unfenced.filter(i => headerLevel(lines(i)).isDefined).toVector
    val requirements =
      (specRequirements(headers) ++ deltaRequirements(lines, fenced, headers)).sortBy(_.index)
    requirements.map(statement(lines, fenced, textEnds, _))

  /** `markdown-parser.js`' `parseSpec`: the first section titled Requirements, in any case and at
    * any level, and each header one level below it before the next header at its level or above.
    */
  private def specRequirements(headers: List[Header]): List[Header] =
    headers.find(_.title.equalsIgnoreCase("Requirements")).toList.flatMap { section =>
      headers
        .dropWhile(_.index <= section.index)
        .takeWhile(_.level > section.level)
        .filter(_.level == section.level + 1)
    }

  /** `requirement-blocks.js`: the sections that `## ` headers start (`splitTopLevelSections`), and
    * in the added and modified ones (titles compared without a locale, so a Turkish one can't turn
    * an I into a dotless ı), each `### Requirement:` line. Its title for the text's fallback is the
    * line after its number signs and whitespace, as the spec's reader titles a header.
    */
  private def deltaRequirements(
      lines: Vector[String],
      fenced: Vector[Boolean],
      headers: List[Header]
  ): List[Header] =
    val sections = headers.filter(_.level == 2)
    sections.zip(sections.drop(1).map(_.index) :+ lines.size).flatMap { (section, end) =>
      if !checkedSections.exists(_.equalsIgnoreCase(section.title)) then Nil
      else
        (section.index + 1 until end).toList.collect {
          case i if !fenced(i) && isRequirementHeader(lines(i)) =>
            Header(i, 3, closed(lines(i).drop(3).dropWhile(isSpace)))
        }
    }

  /** `REQUIREMENT_HEADER_REGEX`, `/^###\s*Requirement:\s*(.+)$/i`: three number signs, optional
    * whitespace, `Requirement:` in any case, and at least one more character on the line.
    */
  private def isRequirementHeader(line: String): Boolean =
    val afterSigns = line.drop(3).dropWhile(isSpace)
    val label = "Requirement:"
    line.startsWith("###") && afterSigns.regionMatches(true, 0, label, 0, label.length) &&
    spacesThenText(afterSigns.drop(label.length))

  /** A header, `/^(#{1,6})\s+(.+)$/`: one to six number signs, whitespace, and at least one more
    * character on the line; its level, and its title (what follows the whitespace) without a
    * closing run.
    */
  private def header(index: Int, line: String): Option[Header] =
    headerLevel(line).flatMap { level =>
      val rest = line.drop(level)
      Option.when(spacesThenText(rest.drop(1)))(
        Header(index, level, closed(rest.dropWhile(isSpace)))
      )
    }

  /** `/^#{1,6}\s/`: one to six number signs and whitespace; the number of signs. */
  private def headerLevel(line: String): Option[Int] =
    val level = line.takeWhile(_ == '#').length
    Option.when(level >= 1 && level <= 6 && line.lift(level).exists(isSpace))(level)

  /** `requirement-text.js`' `extractRequirementText`: the lines after the header up to the next
    * header line, without fenced or blank lines, each trimmed and joined with line breaks; label
    * lines (`**ID**: …`) dropped unless nothing else is left; and with nothing at all, the header's
    * title. The line is the first line taken, or the header's.
    */
  private def statement(
      lines: Vector[String],
      fenced: Vector[Boolean],
      textEnds: Vector[Int],
      requirement: Header
  ): Statement =
    val next = textEnds.search(requirement.index + 1) match
      case Found(at)          => at
      case InsertionPoint(at) => at
    val end = textEnds.lift(next).getOrElse(lines.size)
    val taken =
      (requirement.index + 1 until end).toList.filter(i => !fenced(i) && trim(lines(i)).nonEmpty)
    val (labels, text) = taken.partition(i => isLabel(trim(lines(i))))
    val chosen = if text.nonEmpty then text else labels
    chosen.headOption.fold(Statement(requirement.index + 1, requirement.title))(first =>
      Statement(first + 1, chosen.map(i => trim(lines(i))).mkString("\n"))
    )

  /** `METADATA_LINE`, `/^\*\*[^*]+\*\*:/`, on a trimmed line: a bold label and a colon. */
  private def isLabel(trimmed: String): Boolean =
    val label = trimmed.drop(2).takeWhile(_ != '*')
    trimmed.startsWith("**") && label.nonEmpty && trimmed.drop(2 + label.length).startsWith("**:")

  /** Without a byte-order mark, and with Windows' and old Macs' line endings as `\n`, as
    * `MarkdownParser.normalizeContent` does.
    */
  private def normalized(content: String): Vector[String] =
    val text = if content.headOption.exists(_.toInt == 0xfeff) then content.drop(1) else content
    text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).toVector

  /** `code-fence.js`' `buildCodeFenceMask`: whether each line is in a fenced code block, its fences
    * included. A fence is three or more backticks or tildes, closed by a run of the same character
    * at least as long with only whitespace around it; a backtick run with a backtick after it is
    * inline code, not a fence.
    */
  private def fenceMask(lines: Vector[String]): Vector[Boolean] =
    lines
      .foldLeft((Vector.empty[Boolean], Option.empty[String])) {
        case ((mask, None), line) =>
          fence(line).filterNot((run, rest) => run.startsWith("`") && rest.contains('`')) match
            case Some((run, _)) => (mask :+ true, Some(run))
            case None           => (mask :+ false, None)
        case ((mask, Some(open)), line) =>
          val closes = fence(line).exists((run, rest) =>
            rest.forall(isSpace) && run.head == open.head && run.length >= open.length
          )
          (mask :+ true, Option.unless(closes)(open))
      }
      ._1

  /** A fence's run, `/^\s*(`{3,}|~{3,})/`, and what follows it on its line. */
  private def fence(line: String): Option[(String, String)] =
    val indented = line.dropWhile(isSpace)
    indented.headOption.filter(c => c == '`' || c == '~').flatMap { c =>
      val run = indented.takeWhile(_ == c)
      Option.when(run.length >= 3)((run, indented.drop(run.length)))
    }

  /** A header's title without a closing run of number signs (`stripClosingSequence`, then
    * `trim()`): only a run after a space or tab closes it, so `C#` keeps its sign.
    */
  private def closed(title: String): String =
    val beforeTabs = title.reverse.dropWhile(c => c == ' ' || c == '\t')
    val signs = beforeTabs.takeWhile(_ == '#')
    val beforeSigns = beforeTabs.drop(signs.length)
    val closes = signs.nonEmpty && beforeSigns.headOption.exists(c => c == ' ' || c == '\t')
    trim(if closes then beforeSigns.dropWhile(c => c == ' ' || c == '\t').reverse else title)

  /** `/^\s*.+$/` in JavaScript, whose `.` matches anything but a line terminator: optional
    * whitespace, then at least one character, with no line or paragraph separator after the
    * whitespace (one within it is whitespace).
    */
  private def spacesThenText(text: String): Boolean =
    val lastSeparator = text.lastIndexWhere(c => c.toInt == 0x2028 || c.toInt == 0x2029)
    text.take(lastSeparator + 1).forall(isSpace) && text.length > lastSeparator + 1

  /** JavaScript's `trim()`. */
  private def trim(text: String): String =
    text.dropWhile(isSpace).reverse.dropWhile(isSpace).reverse

  /** JavaScript's whitespace, which its `\s` and `trim()` share: WhiteSpace and LineTerminator in
    * ECMAScript, as code points.
    */
  private def isSpace(c: Char): Boolean =
    c.toInt match
      case 0x09 | 0x0a | 0x0b | 0x0c | 0x0d | 0x20 | 0xa0 | 0x1680 | 0x2028 | 0x2029 | 0x202f |
          0x205f | 0x3000 | 0xfeff =>
        true
      case code => code >= 0x2000 && code <= 0x200a
