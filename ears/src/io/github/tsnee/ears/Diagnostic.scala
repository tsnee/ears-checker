package io.github.tsnee.ears

/** One kind of problem per diagnostic requirement in the `ears-classifier` spec. */
enum DiagnosticKind derives CanEqual:
  case MissingModal, WrongCase, MultipleResponses, WeakModal, EmptyResponse
  case ClauseOrder, RepeatedClause, ConflictingTriggers, MissingThen, MissingComma, MissingArticle
  case MissingPeriod, MultipleSentences, UnrecognizedStructure

/** Replace the text in `span` with `replacement`. */
final case class TextEdit(span: SourceSpan, replacement: String) derives CanEqual

/** Edits that correct one problem. They don't overlap. */
final case class Fix(edits: List[TextEdit]) derives CanEqual

object Fix:

  extension (fix: Fix)

    /** The input with every edit applied. */
    def applyTo(input: String): String =
      fix.edits.sortBy(_.span.start).reverse.foldLeft(input) { (text, edit) =>
        text.substring(0, edit.span.start) + edit.replacement + text.substring(edit.span.end)
      }

final case class Diagnostic(
    kind: DiagnosticKind,
    span: SourceSpan,
    message: String,
    fix: Option[Fix]
) derives CanEqual
