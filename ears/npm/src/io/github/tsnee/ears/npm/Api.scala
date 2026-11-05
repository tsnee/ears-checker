package io.github.tsnee.ears.npm

import cats.data.NonEmptyList
import io.github.tsnee.ears.*

/** A JSON-like value: the shape of a result of the npm package's `classify`, before the Scala.js
  * facade turns it into a JavaScript object.
  */
enum Value derives CanEqual:
  case Null
  case Bool(value: Boolean)
  case Num(value: Int)
  case Str(value: String)
  case Arr(items: List[Value])
  case Obj(fields: List[(String, Value)])

/** What a caller gave as `options.profile`. */
enum ProfileArgument derives CanEqual:
  case Absent
  case Name(name: String)
  case Other

/** Every decision behind the npm package's API, as declared in `index.d.ts`. Results use the
  * command line's JSON conventions: kebab-case names, `span: {start, end}`, and `null` for an
  * absent value.
  */
object Api:

  /** The result of classifying `sentence` under `profile`: `ok: true` with the template and the
    * requirement's clauses, or `ok: false` with every diagnostic, in the library's order.
    */
  def classify(profile: Profile, sentence: String): Value.Obj =
    Requirement.classify(profile, sentence).fold(failure(sentence, _), success)

  /** The profile `options.profile` selects, or the `TypeError` message when it names none. */
  def profile(argument: ProfileArgument): Either[String, Profile] = argument match
    case ProfileArgument.Absent                           => Right(Profile.Classic)
    case ProfileArgument.Name(name) if name == "classic"  => Right(Profile.Classic)
    case ProfileArgument.Name(name) if name == "openspec" => Right(Profile.OpenSpec)
    case _ => Left("classify: options.profile must be \"classic\" or \"openspec\"")

  /** The `TypeError` message for a sentence that isn't a string. */
  val sentenceError: String = "classify: the sentence must be a string"

  /** The `TypeError` message for options that are neither undefined nor an object. */
  val optionsError: String = "classify: options must be an object"

  /** The JavaScript name of a template. Spelled out, like the library's `kebabName` for diagnostic
    * kinds, so that renaming a Scala case can't change the JavaScript API.
    */
  def templateName(template: Template): String = template match
    case Template.Ubiquitous       => "ubiquitous"
    case Template.EventDriven      => "event-driven"
    case Template.StateDriven      => "state-driven"
    case Template.UnwantedBehavior => "unwanted-behavior"
    case Template.OptionalFeature  => "optional-feature"
    case Template.Complex          => "complex"

  private def success(requirement: Requirement): Value.Obj =
    Value.Obj(
      List(
        "ok" -> Value.Bool(true),
        "template" -> Value.Str(templateName(Template.of(requirement))),
        "requirement" -> Value.Obj(
          List(
            "feature" -> requirement.feature.fold(Value.Null)(clause),
            "precondition" -> requirement.precondition.fold(Value.Null)(clause),
            "trigger" -> requirement.trigger.fold(Value.Null)(trigger),
            "system" -> clause(requirement.system),
            "response" -> clause(requirement.response)
          )
        )
      )
    )

  private def failure(sentence: String, diagnostics: NonEmptyList[Diagnostic]): Value.Obj =
    Value.Obj(
      List(
        "ok" -> Value.Bool(false),
        "diagnostics" -> Value.Arr(diagnostics.toList.map(diagnostic(sentence, _)))
      )
    )

  private def diagnostic(sentence: String, found: Diagnostic): Value =
    Value.Obj(
      List(
        "kind" -> Value.Str(found.kind.kebabName),
        "message" -> Value.Str(found.message),
        "span" -> span(found.span),
        "fix" -> found.fix.fold(Value.Null)(fix(sentence, _))
      )
    )

  private def fix(sentence: String, found: Fix): Value =
    Value.Obj(
      List(
        "edits" -> Value.Arr(
          found.edits.map(edit =>
            Value.Obj(List("span" -> span(edit.span), "replacement" -> Value.Str(edit.replacement)))
          )
        ),
        "fixed" -> Value.Str(found.applyTo(sentence))
      )
    )

  private def trigger(found: Trigger): Value = found match
    case Trigger.Event(text)    => triggerOf("event", text)
    case Trigger.Unwanted(text) => triggerOf("unwanted", text)

  private def triggerOf(kind: String, text: Located[String]): Value =
    Value.Obj(List("kind" -> Value.Str(kind), "clause" -> clause(text)))

  private def clause(text: Located[String]): Value =
    Value.Obj(List("text" -> Value.Str(text.value), "span" -> span(text.span)))

  private def span(found: SourceSpan): Value =
    Value.Obj(List("start" -> Value.Num(found.start), "end" -> Value.Num(found.end)))
