package io.github.tsnee.ears.npm

import cats.syntax.all.*
import io.github.tsnee.ears.*
import zio.test.*

/** The npm package's results, read back as the library's types and compared with the library's.
  */
object ApiSuite extends ZIOSpecDefault:

  /** The CLI's rule for JSON names: "WrongCase" becomes "wrong-case". */
  private def kebab(name: String): String =
    name.flatMap(c => if c.isUpper then s"-${c.toLower}" else c.toString).drop(1)

  /** The library's names, which the command line uses too: decoding a kind by any other name fails
    * the property test below.
    */
  private val kindsByName: Map[String, DiagnosticKind] =
    DiagnosticKind.values.map(kind => kind.kebabName -> kind).toMap

  private def field(value: Value, name: String): Option[Value] = value match
    case Value.Obj(fields) => fields.collectFirst { case (`name`, found) => found }
    case _                 => None

  private def string(value: Value): Option[String] = value match
    case Value.Str(text) => Some(text)
    case _               => None

  private def int(value: Value): Option[Int] = value match
    case Value.Num(number) => Some(number)
    case _                 => None

  private def bool(value: Value): Option[Boolean] = value match
    case Value.Bool(flag) => Some(flag)
    case _                => None

  private def elements(value: Value): Option[List[Value]] = value match
    case Value.Arr(items) => Some(items)
    case _                => None

  /** The named field: `None` inside for `null`, or the field read with `read`. */
  private def nullable[A](value: Value, name: String)(read: Value => Option[A]): Option[Option[A]] =
    field(value, name).flatMap {
      case Value.Null => Some(None)
      case found      => read(found).map(Some(_))
    }

  private def at[A](value: Value, name: String)(read: Value => Option[A]): Option[A] =
    field(value, name).flatMap(read)

  private def span(value: Value): Option[SourceSpan] =
    (at(value, "start")(int), at(value, "end")(int)).mapN(SourceSpan(_, _))

  private def clause(value: Value): Option[Located[String]] =
    (at(value, "text")(string), at(value, "span")(span)).mapN(Located(_, _))

  private def trigger(value: Value): Option[Trigger] =
    at(value, "clause")(clause).flatMap(text =>
      at(value, "kind")(string).collect {
        case "event"    => Trigger.Event(text)
        case "unwanted" => Trigger.Unwanted(text)
      }
    )

  private def requirement(value: Value): Option[Requirement] =
    (
      nullable(value, "feature")(clause),
      nullable(value, "precondition")(clause),
      nullable(value, "trigger")(trigger),
      at(value, "system")(clause),
      at(value, "response")(clause)
    ).mapN(Requirement(_, _, _, _, _))

  /** The fix, provided its `fixed` sentence is its edits applied to `sentence`. */
  private def fix(sentence: String)(value: Value): Option[Fix] =
    for
      edits <- at(value, "edits")(elements).flatMap(_.traverse(edit))
      fixed <- at(value, "fixed")(string)
      result = Fix(edits)
      if result.applyTo(sentence) == fixed
    yield result

  private def edit(value: Value): Option[TextEdit] =
    (at(value, "span")(span), at(value, "replacement")(string)).mapN(TextEdit(_, _))

  private def diagnostic(sentence: String)(value: Value): Option[Diagnostic] =
    (
      at(value, "kind")(string).flatMap(kindsByName.get),
      at(value, "span")(span),
      at(value, "message")(string),
      nullable(value, "fix")(fix(sentence))
    ).mapN(Diagnostic(_, _, _, _))

  /** The result read back as the library's types, or `None` if it isn't shaped as declared. A match
    * must also name its template correctly.
    */
  private def decode(
      sentence: String,
      result: Value
  ): Option[Either[List[Diagnostic], Requirement]] =
    at(result, "ok")(bool).flatMap {
      case true =>
        at(result, "requirement")(requirement)
          .filter(found =>
            at(result, "template")(string).contains(kebab(Template.of(found).toString))
          )
          .map(Right(_))
      case false =>
        at(result, "diagnostics")(elements)
          .flatMap(_.traverse(diagnostic(sentence)))
          .map(Left(_))
    }

  private def library(profile: Profile, sentence: String) =
    Requirement.classify(profile, sentence).left.map(_.toList)

  private val eventDriven =
    "When a user submits an order, the checkout service shall send a confirmation email."
  private val ubiquitous = "The scheduler shall list upcoming events."

  private def found(sentence: String): Option[Requirement] =
    at(Api.classify(Profile.Classic, sentence), "requirement")(requirement)

  private def diagnostics(profile: Profile, sentence: String): List[Value] =
    at(Api.classify(profile, sentence), "diagnostics")(elements).toList.flatten

  /** A profile and a sentence: a valid requirement rendered under that profile, a damaged sentence,
    * or arbitrary text.
    */
  private val sentenceUnderProfile: Gen[Any, (String, Profile)] =
    Gen
      .elements(Profile.Classic, Profile.OpenSpec)
      .flatMap(profile =>
        Gen
          .oneOf(
            Generators.validRequirement(profile).map((_, sentence) => sentence),
            Generators.damagedSentence,
            Gen.string
          )
          .map((_, profile))
      )

  def spec = suite("npm package results")(
    suite("matching sentences")(
      test("event-driven: the template and every clause"):
        val result = Api.classify(Profile.Classic, eventDriven)
        val requirementField = field(result, "requirement")
        assertTrue(
          at(result, "ok")(bool).contains(true),
          at(result, "template")(string).contains("event-driven"),
          found(eventDriven)
            .flatMap(_.trigger)
            .contains(
              Trigger.Event(Located("a user submits an order", SourceSpan(5, 28)))
            ),
          found(eventDriven).map(_.system.value).contains("checkout service"),
          found(eventDriven).map(_.response.value).contains("send a confirmation email"),
          requirementField.flatMap(field(_, "feature")).contains(Value.Null),
          requirementField.flatMap(field(_, "precondition")).contains(Value.Null)
        )
      ,
      test("unwanted behavior: an unwanted trigger"):
        val sentence = "If a payment fails, then the billing service shall retry the charge."
        assertTrue(
          at(Api.classify(Profile.Classic, sentence), "template")(string)
            .contains("unwanted-behavior"),
          found(sentence).flatMap(_.trigger).exists {
            case Trigger.Unwanted(_) => true
            case Trigger.Event(_)    => false
          }
        )
      ,
      test("ubiquitous: no leading clauses"):
        assertTrue(
          at(Api.classify(Profile.Classic, ubiquitous), "template")(string).contains("ubiquitous"),
          found(ubiquitous).map(_.trigger).contains(None)
        )
      ,
      test("complex: a feature and a precondition"):
        val sentence =
          "Where audit logging is enabled, while a session is open, the gateway shall record each request."
        assertTrue(
          at(Api.classify(Profile.Classic, sentence), "template")(string).contains("complex"),
          found(sentence).flatMap(_.feature).map(_.value).contains("audit logging is enabled"),
          found(sentence).flatMap(_.precondition).map(_.value).contains("a session is open")
        )
      ,
      test("a span slices the sentence to its clause's text"):
        val system = found(ubiquitous).map(_.system)
        assertTrue(
          system
            .map(text => ubiquitous.substring(text.span.start, text.span.end))
            .contains("scheduler")
        )
    ),
    suite("sentences that don't match")(
      test("a missing modal"):
        val sentence = "The scheduler lists upcoming events."
        val reported = diagnostics(Profile.Classic, sentence)
        assertTrue(
          at(Api.classify(Profile.Classic, sentence), "ok")(bool).contains(false),
          reported.flatMap(at(_, "kind")(string)).contains("missing-modal"),
          reported.flatMap(at(_, "message")(string)).forall(_.nonEmpty)
        )
      ,
      test("wrong case under the OpenSpec profile: the fix and its fixed sentence"):
        val reported = diagnostics(Profile.OpenSpec, ubiquitous)
        assertTrue(
          reported.flatMap(at(_, "kind")(string)) == List("wrong-case"),
          reported.flatMap(d => field(d, "fix").flatMap(at(_, "fixed")(string))) ==
            List("The scheduler SHALL list upcoming events.")
        )
      ,
      test("a diagnostic with no fix in the library has fix: null"):
        val sentence = "Upcoming events."
        val unfixable = library(Profile.Classic, sentence).left.toOption.toList.flatten
          .map(_.fix.isEmpty)
        assertTrue(
          unfixable.contains(true),
          diagnostics(Profile.Classic, sentence).map(field(_, "fix").contains(Value.Null)) ==
            unfixable
        )
      ,
      test("every result has the library's diagnostics, or its requirement, read back exactly"):
        check(sentenceUnderProfile) { (sentence, profile) =>
          assertTrue(
            decode(sentence, Api.classify(profile, sentence)) == Some(library(profile, sentence))
          )
        }
    ),
    suite("names")(
      test("each template is named in kebab case"):
        assertTrue(
          Template.values.toList.map(Api.templateName) ==
            Template.values.toList.map(template => kebab(template.toString))
        )
    ),
    suite("profile option")(
      test("absent: the classic profile"):
        assertTrue(Api.profile(ProfileArgument.Absent) == Right(Profile.Classic))
      ,
      test("classic and openspec select their profiles"):
        assertTrue(
          Api.profile(ProfileArgument.Name("classic")) == Right(Profile.Classic),
          Api.profile(ProfileArgument.Name("openspec")) == Right(Profile.OpenSpec)
        )
      ,
      test("an unknown name: a message naming both profiles"):
        assertTrue(
          Api
            .profile(ProfileArgument.Name("open-spec"))
            .left
            .exists(message => message.contains("classic") && message.contains("openspec"))
        )
      ,
      test("a value that isn't a string: the same message"):
        assertTrue(
          Api.profile(ProfileArgument.Other) == Api.profile(ProfileArgument.Name("open-spec"))
        )
    )
  )
