package io.github.tsnee.ears.markdown

/** A paragraph's content as cmark reports it, before its lines are corrected. */
private[markdown] enum CmarkPiece derives CanEqual:
  /** Text or inline code, with the start and end lines cmark gives it. */
  case Inline(text: String, code: Boolean, start: Int, end: Int)

  /** A soft or hard line break: always a real line ending in the source. */
  case Break

/** Lines for a paragraph read with cmark, whose inline lines are wrong in places (found by review
  * and C probes of 0.31.2): it doesn't advance after a backslash hard break or after a link whose
  * title is on the next line, and a paragraph that follows a reference definition starts on the
  * definition's line, along with everything in it. What cmark gets right is the paragraph's end
  * line, that each break ends a line, and the lines of inline nodes relative to each other between
  * two breaks. So the last stretch is placed at the end line, each earlier stretch ends one line
  * before the next begins (less the lines its own nodes span, a code span across lines say), and
  * each piece keeps its line relative to its stretch's first node.
  */
private[markdown] object CmarkLines:

  /** The paragraph with corrected lines, from cmark's pieces and its (reliable) end line. */
  def paragraph(pieces: List[CmarkPiece], endLine: Int): Paragraph =
    val stretches = split(pieces)
    val starts = stretches.foldRight(List.empty[Int]) { (stretch, later) =>
      val next = later.headOption.fold(endLine + 1)(identity)
      (next - 1 - span(stretch)) :: later
    }
    val placed = stretches.zip(starts).flatMap((stretch, start) => place(stretch, start))
    Paragraph(starts.headOption.fold(endLine)(identity), placed)

  /** The stretches between breaks, each with the break that ends it (none for the last). */
  private def split(pieces: List[CmarkPiece]): List[List[CmarkPiece]] =
    // Right to left: the stretch being built, and the finished ones after it. A break ends the
    // stretch to its left, so it starts the next one built.
    val (first, rest) =
      pieces.foldRight((List.empty[CmarkPiece], List.empty[List[CmarkPiece]])) {
        case (CmarkPiece.Break, (current, done)) => (List(CmarkPiece.Break), current :: done)
        case (piece, (current, done))            => (piece :: current, done)
      }
    first :: rest

  private def inlines(stretch: List[CmarkPiece]): List[CmarkPiece.Inline] =
    stretch.collect { case inline: CmarkPiece.Inline => inline }

  /** How many lines past its first the stretch's nodes reach, by cmark's count. */
  private def span(stretch: List[CmarkPiece]): Int =
    val found = inlines(stretch)
    (found.map(_.end).maxOption, found.map(_.start).minOption) match
      case (Some(last), Some(first)) => (last - first).max(0)
      case _                         => 0

  private def place(stretch: List[CmarkPiece], start: Int): List[Piece] =
    val first = inlines(stretch).map(_.start).minOption.fold(0)(identity)
    stretch.map {
      case CmarkPiece.Inline(text, code, line, _) => Piece(text, code, Some(start + line - first))
      case CmarkPiece.Break                       => Piece(" ", false, None)
    }
