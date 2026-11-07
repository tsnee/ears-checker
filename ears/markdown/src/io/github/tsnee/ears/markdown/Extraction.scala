package io.github.tsnee.ears.markdown

/** Finding sentences in a parsed paragraph: the part of reading Markdown that doesn't depend on the
  * parser.
  */
private[markdown] object Extraction:

  private val keyword = "(?i)\\bshall\\b".r

  /** Each sentence of `paragraph` that contains the modal keyword outside inline code. A sentence's
    * line is the line of the piece it starts in, or the paragraph's.
    */
  def sentences(paragraph: Paragraph): List[Sentence] =
    // Code next to code (with only dropped HTML between) is one code span, so it's written as one.
    val joined = paragraph.pieces.foldRight(List.empty[Piece]) {
      case (piece, next :: rest) if piece.code && next.code =>
        piece.copy(text = piece.text + next.text) :: rest
      case (piece, rest) => piece :: rest
    }
    val pieces = joined
      .zip(joined.drop(1).map(_.code) :+ false)
      .map((piece, beforeCode) =>
        piece.copy(text =
          if piece.code then codeSpan(piece.text) else escapedText(piece.text, beforeCode)
        )
      )
    val text = pieces.map(_.text).mkString
    val placed = pieces.zip(pieces.scanLeft(0)(_ + _.text.length))
    val codeRanges = placed.collect {
      case (piece, start) if piece.code => (start, start + piece.text.length)
    }
    def inCode(offset: Int): Boolean =
      codeRanges.exists((from, to) => from <= offset && offset < to)
    def lineAt(offset: Int): Int =
      placed
        .collectFirst {
          case (Piece(pieceText, _, Some(line)), start)
              if start <= offset && offset < start + pieceText.length =>
            line
        }
        .getOrElse(paragraph.line)
    bounds(text, inCode).collect {
      case (from, to)
          if keyword
            .findAllMatchIn(text.substring(from, to))
            .exists(found => !inCode(from + found.start)) =>
        Sentence(lineAt(from), text.substring(from, to))
    }

  /** Inline code as CommonMark writes it, which the classifier reads back as one code span: a fence
    * one backtick longer than the longest run of backticks in `literal`, with a space of padding
    * each side when `literal` starts or ends with a backtick.
    */
  def codeSpan(literal: String): String =
    val longest = "`+".r.findAllIn(literal).map(_.length).maxOption.getOrElse(0)
    val fence = "`" * (longest + 1)
    val padding = if literal.startsWith("`") || literal.endsWith("`") then " " else ""
    s"$fence$padding$literal$padding$fence"

  /** Text with each backtick escaped, so the classifier never pairs one into a code span that
    * Markdown didn't make; and, since a backslash escapes a backslash, each backslash doubled that
    * comes right before a backtick: one in the text, or, `beforeCode`, a code span's fence after
    * it. Other backslashes stay single, as Markdown shows them.
    */
  def escapedText(text: String, beforeCode: Boolean): String =
    // From each position, whether a run of backslashes there leads to a backtick. (No regex:
    // Scala Native's has no lookahead.)
    val leadsToBacktick =
      text.scanRight(beforeCode)((c, next) => if c == '\\' then next else c == '`')
    text.indices.map { i =>
      text.charAt(i) match
        case '`'                            => "\\`"
        case '\\' if leadsToBacktick(i + 1) => "\\\\"
        case c                              => c.toString
    }.mkString

  /** Sentences end at a period outside inline code followed by whitespace, the classifier's own
    * sentence rule. Each is trimmed of surrounding whitespace; a run of only whitespace is no
    * sentence.
    */
  private def bounds(text: String, inCode: Int => Boolean): List[(Int, Int)] =
    val ends = (0 until text.length - 1).toList.collect {
      case i if text.charAt(i) == '.' && !inCode(i) && Character.isWhitespace(text.charAt(i + 1)) =>
        i + 1
    }
    (0 :: ends).zip(ends :+ text.length).flatMap { (start, end) =>
      val trimmedStart = (start until end).find(i => !Character.isWhitespace(text.charAt(i)))
      val trimmedEnd = (start until end).reverse.find(i => !Character.isWhitespace(text.charAt(i)))
      for
        from <- trimmedStart
        to <- trimmedEnd
      yield (from, to + 1)
    }
