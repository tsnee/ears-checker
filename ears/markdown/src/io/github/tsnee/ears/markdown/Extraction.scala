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
    val pieces = paragraph.pieces
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
    bounds(text).collect {
      case (from, to)
          if keyword
            .findAllMatchIn(text.substring(from, to))
            .exists(found => !inCode(from + found.start)) =>
        Sentence(lineAt(from), text.substring(from, to))
    }

  /** Sentences end at a period followed by whitespace, the classifier's own sentence rule. Each is
    * trimmed of surrounding whitespace; a run of only whitespace is no sentence.
    */
  private def bounds(text: String): List[(Int, Int)] =
    val ends = (0 until text.length - 1).toList.collect {
      case i if text.charAt(i) == '.' && Character.isWhitespace(text.charAt(i + 1)) => i + 1
    }
    (0 :: ends).zip(ends :+ text.length).flatMap { (start, end) =>
      val trimmedStart = (start until end).find(i => !Character.isWhitespace(text.charAt(i)))
      val trimmedEnd = (start until end).reverse.find(i => !Character.isWhitespace(text.charAt(i)))
      for
        from <- trimmedStart
        to <- trimmedEnd
      yield (from, to + 1)
    }
