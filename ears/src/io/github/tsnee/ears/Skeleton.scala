package io.github.tsnee.ears

import cats.parse.Parser
import cats.parse.Parser0
import io.github.tsnee.ears.LetterCase.*

/** Where the parts of a sentence are, found by a lenient parse that never fails. Validation judges
  * the result; nothing here decides whether the sentence is good EARS.
  */
private[ears] final case class Skeleton(
    shape: Shape,
    period: Option[SourceSpan],
    extraText: Option[SourceSpan],
    sentenceEnd: Int,
    keywords: List[SourceSpan]
) derives CanEqual

private[ears] object Skeleton:
  def of(input: String): Skeleton = SkeletonParser.parse(input)

/** The structure of the first sentence, anchored on its modal word. */
private[ears] enum Shape derives CanEqual:
  case Anchored(
      clauses: List[RawClause],
      thenWord: Option[SourceSpan],
      article: Option[Located[String]],
      system: Located[String],
      modal: Located[String],
      response: Located[String]
  )
  case NoModal
  case Unrecognized(span: SourceSpan)

/** A leading clause as written: its keyword in any case, its text, and the comma after it. */
private[ears] final case class RawClause(
    keyword: Located[String],
    text: Located[String],
    comma: Option[SourceSpan]
) derives CanEqual

private[ears] object SkeletonParser:

  private enum Token derives CanEqual:
    case Word(text: String, span: SourceSpan)
    case Comma(span: SourceSpan)
    case Period(span: SourceSpan)

  private object Token:

    extension (token: Token)
      def span: SourceSpan = token match
        case Word(_, span) => span
        case Comma(span)   => span
        case Period(span)  => span

  /** Comma-separated words before the modal word, with the comma that ends them, if any. */
  private final case class Segment(words: List[Token.Word], comma: Option[SourceSpan])

  private val clauseKeywords = Set("where", "while", "when", "if")
  private val weakModals = Set("must", "should", "may", "will", "can", "could", "might", "would")

  def parse(input: String): Skeleton =
    tokens.parseAll(input) match
      case Left(_) =>
        Skeleton(Shape.Unrecognized(SourceSpan(0, input.length)), None, None, input.length, Nil)
      case Right(parsed) => fromTokens(input, parsed)

  // Tokens. A comma or period is punctuation only before whitespace or the end of the input, so
  // "10,000", "1.5", and "e.g" stay inside words.

  private val space: Parser[Unit] = Parser.charWhere(Character.isWhitespace).rep.void
  private val boundary: Parser0[Unit] = Parser.oneOf0(List(space, Parser.end))

  private val wordChar: Parser[Unit] = Parser.oneOf(
    List(
      Parser.charWhere(c => !Character.isWhitespace(c) && c != ',' && c != '.' && c != '`').void,
      (Parser.charIn(',', '.') *> Parser.not(boundary)).backtrack
    )
  )

  // Inline code, as in CommonMark: a run of backticks opens a code span that closes at the next
  // run of the same length. The span is part of a word, so its spaces, commas, periods, and words
  // are never punctuation, keywords, or modals. A run with no matching run is ordinary characters,
  // all of it; so is a backslash-escaped backtick. As in Markdown, a backslash escapes a backslash,
  // so in \\` the backtick opens code.

  private val backtickRun: Parser[String] = Parser.char('`').rep.string

  private val escapedBacktick: Parser[Unit] = (Parser.char('\\') ~ Parser.char('`')).void

  private val escapedBackslash: Parser[Unit] = (Parser.char('\\') ~ Parser.char('\\')).void

  private val codeSpanOrBackticks: Parser[Unit] = backtickRun.flatMap { open =>
    val content = Parser.oneOf(
      List(
        Parser.charWhere(_ != '`').void,
        backtickRun.filter(_.length != open.length).backtrack.void
      )
    )
    (content.rep0.with1 ~ backtickRun.filter(_.length == open.length)).void.backtrack
      .orElse(Parser.unit)
  }

  private val wordPart: Parser[Unit] =
    Parser.oneOf(
      List(escapedBackslash.backtrack, escapedBacktick.backtrack, codeSpanOrBackticks, wordChar)
    )

  private def located[A](parser: Parser[A]): Parser[(A, SourceSpan)] =
    (Parser.index.with1 ~ parser ~ Parser.index).map { case ((start, value), end) =>
      (value, SourceSpan(start, end))
    }

  private val token: Parser[Token] = Parser.oneOf(
    List(
      located(wordPart.rep.string).map(Token.Word(_, _)),
      located(Parser.char(',')).map((_, span) => Token.Comma(span)),
      located(Parser.char('.')).map((_, span) => Token.Period(span))
    )
  )

  private val tokens: Parser0[List[Token]] = space.? *> (token <* space.?).rep0 <* Parser.end

  // Sentence and shape.

  private def fromTokens(input: String, all: List[Token]): Skeleton =
    val firstPeriod = all.indexWhere {
      case Token.Period(_) => true
      case _               => false
    }
    val (sentence, rest) = if firstPeriod < 0 then (all, Nil) else all.splitAt(firstPeriod)
    val extraText = rest.drop(1) match
      case Nil  => None
      case more => coverOf(more)
    val keywords = all.collect { case Token.Word(text, span) if isShall(text) => span }
    Skeleton(
      shapeOf(input, sentence),
      rest.headOption.map(_.span),
      extraText,
      coverOf(sentence).fold(0)(_.end),
      keywords
    )

  private def coverOf(tokens: List[Token]): Option[SourceSpan] =
    for
      first <- tokens.headOption
      last <- tokens.lastOption
    yield SourceSpan(first.span.start, last.span.end)

  private def wordIs(token: Token, test: String => Boolean): Boolean = token match
    case Token.Word(text, _) => test(text)
    case _                   => false

  private def isShall(text: String): Boolean = text.equalsIgnoreCase("shall")
  private def isThe(text: String): Boolean = text.equalsIgnoreCase("the")
  private def isThen(text: String): Boolean = text.equalsIgnoreCase("then")
  private def isClauseKeyword(text: String): Boolean =
    clauseKeywords.contains(text.lowerCase)
  private def isWeakModal(text: String): Boolean =
    weakModals.contains(text.lowerCase)

  /** The first "shall" in any case anchors the main clause. Failing that, the first weak modal
    * whose preceding words can be a main clause does.
    */
  private def shapeOf(input: String, sentence: List[Token]): Shape =
    val indexed = sentence.zipWithIndex
    val shallAt = indexed.collectFirst { case (token, i) if wordIs(token, isShall) => i }
    shallAt match
      case Some(i) =>
        anchoredAt(input, sentence, i)
          .getOrElse(Shape.Unrecognized(coverOf(sentence).getOrElse(SourceSpan(0, 0))))
      case None =>
        indexed
          .collect { case (token, i) if wordIs(token, isWeakModal) => i }
          .flatMap(anchoredAt(input, sentence, _))
          .headOption
          .getOrElse(Shape.NoModal)

  private def anchoredAt(input: String, sentence: List[Token], at: Int): Option[Shape.Anchored] =
    sentence.lift(at).collect { case word: Token.Word => word }.flatMap { modal =>
      segmentsOf(sentence.take(at)).reverse match
        case prefix :: leadingReversed =>
          for
            leading <- leadingClauses(input, leadingReversed.reverse)
            (missingComma, mainWords) <- splitMissingComma(input, prefix.words)
            (thenWord, afterThen) = splitThen(mainWords)
            (article, systemWords) = splitArticle(afterThen)
            system <- Option.when(systemWords.nonEmpty)(
              textOf(input, systemWords, modal.span.start)
            )
          yield Shape.Anchored(
            leading ++ missingComma.toList,
            thenWord,
            article,
            system,
            Located(modal.text, modal.span),
            responseOf(input, sentence.drop(at + 1), modal.span.end)
          )
        case Nil => None
    }

  private def segmentsOf(tokens: List[Token]): List[Segment] =
    tokens
      .foldLeft(List(Segment(Nil, None))) { (segments, token) =>
        (segments, token) match
          case (current :: done, word: Token.Word) =>
            current.copy(words = current.words :+ word) :: done
          case (current :: done, Token.Comma(span)) =>
            Segment(Nil, None) :: current.copy(comma = Some(span)) :: done
          case _ => segments
      }
      .reverse

  /** A segment that starts with a clause keyword followed by text starts a clause; any other
    * segment continues the previous clause, so commas inside clause text survive. A keyword with no
    * text before its comma (as in "the order Where, While, then When") can't be a clause, so it
    * continues the previous one too. A segment before any clause, or an empty one, means the
    * sentence has no recognizable shape.
    */
  private def leadingClauses(input: String, segments: List[Segment]): Option[List[RawClause]] =
    segments.foldLeft(Option(List.empty[RawClause])) { (accumulated, segment) =>
      accumulated.flatMap { clauses =>
        segment.words match
          case keyword :: textWords if isClauseKeyword(keyword.text) && textWords.nonEmpty =>
            Some(
              clauses :+ RawClause(
                Located(keyword.text, keyword.span),
                textOf(input, textWords, keyword.span.end),
                segment.comma
              )
            )
          case words =>
            for
              previous <- clauses.lastOption
              last <- words.lastOption
            yield
              val span = SourceSpan(previous.text.span.start, last.span.end)
              clauses.dropRight(1) :+ previous.copy(
                text = Located(input.substring(span.start, span.end), span),
                comma = segment.comma
              )
      }
    }

  /** Words before the modal that start with a clause keyword are a clause whose comma is missing;
    * its text ends at the last "the", which starts the system name. A "then" just before that "the"
    * belongs to the main clause, not to the clause text.
    */
  private def splitMissingComma(
      input: String,
      words: List[Token.Word]
  ): Option[(Option[RawClause], List[Token.Word])] =
    words match
      case keyword :: _ if isClauseKeyword(keyword.text) =>
        val lastThe = words.lastIndexWhere(word => isThe(word.text))
        Option.when(lastThe >= 2) {
          val (clauseWords, mainWords) = words.splitAt(lastThe)
          val textWords = clauseWords.drop(1)
          val (clauseText, thenWord) = textWords.lastOption match
            case Some(last) if isThen(last.text) && textWords.size > 1 =>
              (textWords.dropRight(1), List(last))
            case _ => (textWords, Nil)
          val clause = RawClause(
            Located(keyword.text, keyword.span),
            textOf(input, clauseText, keyword.span.end),
            None
          )
          (Some(clause), thenWord ++ mainWords)
        }
      case Nil => None
      case _   => Some((None, words))

  private def splitThen(words: List[Token.Word]): (Option[SourceSpan], List[Token.Word]) =
    words match
      case first :: rest if isThen(first.text) => (Some(first.span), rest)
      case _                                   => (None, words)

  private def splitArticle(words: List[Token.Word]): (Option[Located[String]], List[Token.Word]) =
    words match
      case first :: rest if isThe(first.text) => (Some(Located(first.text, first.span)), rest)
      case _                                  => (None, words)

  private def responseOf(input: String, tokens: List[Token], modalEnd: Int): Located[String] =
    coverOf(tokens) match
      case Some(span) => Located(input.substring(span.start, span.end), span)
      case None       => Located("", SourceSpan(modalEnd, modalEnd))

  /** The input text covered by `words`, or an empty text at `emptyAt` when there are none. */
  private def textOf(input: String, words: List[Token.Word], emptyAt: Int): Located[String] =
    coverOf(words) match
      case Some(span) => Located(input.substring(span.start, span.end), span)
      case None       => Located("", SourceSpan(emptyAt, emptyAt))
