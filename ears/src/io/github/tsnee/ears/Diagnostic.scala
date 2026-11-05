package io.github.tsnee.ears

/** One kind of problem per diagnostic requirement in the `ears-classifier` spec. */
enum DiagnosticKind derives CanEqual:
  case MissingModal, WrongCase, MultipleResponses, WeakModal, EmptyResponse
  case ClauseOrder, RepeatedClause, ConflictingTriggers, MissingThen, MissingComma, MissingArticle
  case MissingPeriod, MultipleSentences, UnrecognizedStructure

object DiagnosticKind:

  extension (kind: DiagnosticKind)

    /** The kind's name in output, in kebab case ("wrong-case"): the command line's text and JSON,
      * and the npm package. Spelled out, so that renaming a case can't change any output.
      */
    def kebabName: String = kind match
      case MissingModal          => "missing-modal"
      case WrongCase             => "wrong-case"
      case MultipleResponses     => "multiple-responses"
      case WeakModal             => "weak-modal"
      case EmptyResponse         => "empty-response"
      case ClauseOrder           => "clause-order"
      case RepeatedClause        => "repeated-clause"
      case ConflictingTriggers   => "conflicting-triggers"
      case MissingThen           => "missing-then"
      case MissingComma          => "missing-comma"
      case MissingArticle        => "missing-article"
      case MissingPeriod         => "missing-period"
      case MultipleSentences     => "multiple-sentences"
      case UnrecognizedStructure => "unrecognized-structure"

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
