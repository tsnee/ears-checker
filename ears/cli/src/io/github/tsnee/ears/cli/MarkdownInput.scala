package io.github.tsnee.ears.cli

import scala.jdk.CollectionConverters.*

import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** Requirement sentences in Markdown, found with commonmark-java so code is skipped and every
  * sentence keeps its line. Only lines are reported: commonmark-java merges decoded text nodes and
  * their source spans, so offsets into decoded text can't be mapped back to source columns after
  * escapes and entities. This is the only commonmark-java dependency.
  */
private[cli] object MarkdownInput:

  /** A run of paragraph text, whether it is inline code, and the 0-based line it starts on. */
  private final case class Piece(text: String, code: Boolean, line: Option[Int])

  private val parser =
    Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()

  private val keyword = "(?i)\\bshall\\b".r

  def candidates(file: String, content: String): List[Candidate] =
    paragraphs(parser.parse(content)).flatMap { paragraph =>
      fromPieces(file, line(paragraph).getOrElse(0), pieces(paragraph))
    }

  private def children(node: Node): List[Node] =
    List.unfold(Option(node.getFirstChild))(child => child.map(c => (c, Option(c.getNext))))

  private def paragraphs(node: Node): List[Paragraph] = node match
    case paragraph: Paragraph => List(paragraph)
    case other                => children(other).flatMap(paragraphs)

  private def line(node: Node): Option[Int] =
    node.getSourceSpans.asScala.headOption.map(_.getLineIndex)

  private def pieces(node: Node): List[Piece] = node match
    case text: Text       => List(Piece(Option(text.getLiteral).getOrElse(""), false, line(text)))
    case code: Code       => List(Piece(Option(code.getLiteral).getOrElse(""), true, line(code)))
    case _: SoftLineBreak => List(Piece(" ", false, None))
    case _: HardLineBreak => List(Piece(" ", false, None))
    case other            => children(other).flatMap(pieces)

  /** Sentences end at a period followed by whitespace, the classifier's own sentence rule. */
  private def sentences(text: String): List[(Int, Int)] =
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

  private def fromPieces(file: String, paragraphLine: Int, pieces: List[Piece]): List[Candidate] =
    val text = pieces.map(_.text).mkString
    val starts = pieces.scanLeft(0)(_ + _.text.length)
    val placed = pieces.zip(starts)
    val codeRanges = placed.collect {
      case (piece, start) if piece.code => (start, start + piece.text.length)
    }
    def inCode(offset: Int): Boolean =
      codeRanges.exists((from, to) => from <= offset && offset < to)
    def location(offset: Int): Location =
      val found = placed.collectFirst {
        case (Piece(pieceText, _, Some(line)), start)
            if start <= offset && offset < start + pieceText.length =>
          line
      }
      Location.Line(file, found.getOrElse(paragraphLine) + 1)
    sentences(text).collect {
      case (from, to)
          if keyword
            .findAllMatchIn(text.substring(from, to))
            .exists(m => !inCode(from + m.start)) =>
        Candidate(location(from), text.substring(from, to))
    }
