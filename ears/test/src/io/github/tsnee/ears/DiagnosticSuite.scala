package io.github.tsnee.ears

import DiagnosticKind.*
import zio.test.*

object DiagnosticSuite extends ZIOSpecDefault:

  private def diagnostics(profile: Profile, input: String): List[Diagnostic] =
    Requirement.classify(profile, input).left.toOption.map(_.toList).getOrElse(Nil)

  private def kinds(input: String): List[DiagnosticKind] =
    diagnostics(Profile.OpenSpec, input).map(_.kind)

  private def fixed(input: String, kind: DiagnosticKind): Option[String] =
    diagnostics(Profile.OpenSpec, input)
      .find(_.kind == kind)
      .flatMap(_.fix)
      .map(_.applyTo(input))

  def spec = suite("Diagnostics")(
    suite("modal")(
      test("missing modal"):
        assertTrue(kinds("The checkout service displays the order total.").contains(MissingModal))
      ,
      test("wrong case, with a fix"):
        val input = "The scheduler shall list upcoming events."
        assertTrue(
          kinds(input).contains(WrongCase),
          fixed(input, WrongCase).contains("The scheduler SHALL list upcoming events.")
        )
      ,
      test("multiple responses"):
        assertTrue(
          kinds("The checkout service SHALL save the cart and SHALL email a receipt.")
            .contains(MultipleResponses)
        )
      ,
      test("weak modal, with a fix"):
        val input = "The scheduler should list upcoming events."
        assertTrue(
          kinds(input).contains(WeakModal),
          fixed(input, WeakModal).contains("The scheduler SHALL list upcoming events.")
        )
      ,
      test("weak modal in uppercase, with a fix"):
        val input = "The scheduler MUST list upcoming events."
        assertTrue(
          kinds(input).contains(WeakModal),
          fixed(input, WeakModal).contains("The scheduler SHALL list upcoming events.")
        )
      ,
      test("a weak modal is not also a missing modal"):
        assertTrue(!kinds("The scheduler should list upcoming events.").contains(MissingModal))
      ,
      test("empty response"):
        assertTrue(kinds("The scheduler SHALL.").contains(EmptyResponse))
    ),
    suite("clauses")(
      test("clause order, with a reordering fix"):
        val input =
          "When the session expires, while a user is signed in, the access service SHALL redirect the user to the sign-in page."
        assertTrue(
          kinds(input).contains(ClauseOrder),
          fixed(input, ClauseOrder).contains(
            "While a user is signed in, when the session expires, the access service SHALL redirect the user to the sign-in page."
          )
        )
      ,
      test("repeated clause keyword"):
        assertTrue(
          kinds(
            "When a door opens, when the alarm is armed, the access service SHALL sound the alarm."
          )
            .contains(RepeatedClause)
        )
      ,
      test("conflicting triggers"):
        assertTrue(
          kinds(
            "When a door opens, if the alarm is armed, then the access service SHALL sound the alarm."
          ).contains(ConflictingTriggers)
        )
      ,
      test("missing then, with a fix"):
        val input =
          "If the inventory service is unreachable, the checkout service SHALL show a stock warning."
        assertTrue(
          kinds(input).contains(MissingThen),
          fixed(input, MissingThen).contains(
            "If the inventory service is unreachable, then the checkout service SHALL show a stock warning."
          )
        )
      ,
      test("missing then is reported for an If clause that is not the last clause"):
        val found = kinds(
          "If the door opens, while the alarm is armed, the access service SHALL sound the alarm."
        )
        assertTrue(found.contains(ClauseOrder), found.contains(MissingThen))
      ,
      test("applying every fix together yields a sentence with no diagnostics"):
        val input =
          "If the door opens, while the alarm is armed, the access service SHALL sound the alarm."
        val allFixes =
          Fix(diagnostics(Profile.OpenSpec, input).flatMap(_.fix.toList.flatMap(_.edits)))
        val result = allFixes.applyTo(input)
        assertTrue(
          result ==
            "While the alarm is armed, if the door opens, then the access service SHALL sound the alarm.",
          diagnostics(Profile.OpenSpec, result).isEmpty
        )
      ,
      test("missing article: an indefinite article is replaced, not doubled"):
        assertTrue(
          fixed("A scheduler SHALL list upcoming events.", MissingArticle)
            .contains("The scheduler SHALL list upcoming events."),
          fixed("An alarm SHALL sound.", MissingArticle).contains("The alarm SHALL sound.")
        )
      ,
      test("missing article: a possessive is replaced"):
        assertTrue(
          fixed("Our scheduler SHALL list upcoming events.", MissingArticle)
            .contains("The scheduler SHALL list upcoming events.")
        )
      ,
      test("missing article: a quantifier is reported without a fix"):
        val found = diagnostics(Profile.OpenSpec, "Each scheduler SHALL list upcoming events.")
          .filter(_.kind == MissingArticle)
        assertTrue(found.size == 1, found.forall(_.fix.isEmpty))
      ,
      test("missing article: an acronym keeps its case"):
        assertTrue(
          fixed("API gateway SHALL reject unsigned requests.", MissingArticle)
            .contains("The API gateway SHALL reject unsigned requests.")
        )
      ,
      test("missing article: a name with inner capitals keeps its case"):
        assertTrue(
          fixed("OpenSpec SHALL validate every change.", MissingArticle)
            .contains("The OpenSpec SHALL validate every change.")
        )
      ,
      test("missing article: an indefinite article after a clause is replaced"):
        assertTrue(
          fixed("When a door opens, a camera SHALL record the entrance.", MissingArticle)
            .contains("When a door opens, the camera SHALL record the entrance.")
        )
      ,
      test("missing comma, with a fix"):
        val input =
          "When a customer submits an order the checkout service SHALL send a confirmation email."
        assertTrue(
          kinds(input).contains(MissingComma),
          fixed(input, MissingComma).contains(
            "When a customer submits an order, the checkout service SHALL send a confirmation email."
          )
        )
      ,
      test("missing article, with a fix"):
        val input = "Scheduler SHALL list upcoming events."
        assertTrue(
          kinds(input).contains(MissingArticle),
          fixed(input, MissingArticle).contains("The scheduler SHALL list upcoming events.")
        )
    ),
    suite("sentence")(
      test("missing period, with a fix"):
        val input = "The scheduler SHALL list upcoming events"
        assertTrue(
          kinds(input).contains(MissingPeriod),
          fixed(input, MissingPeriod).contains("The scheduler SHALL list upcoming events.")
        )
      ,
      test("multiple sentences"):
        assertTrue(
          kinds("The scheduler SHALL list upcoming events. It SHALL hide past events.")
            .contains(MultipleSentences)
        )
      ,
      test("unrecognized structure"):
        assertTrue(
          kinds("SHALL the scheduler list upcoming events.").contains(UnrecognizedStructure)
        )
      ,
      test("independent problems: wrong case and missing period together"):
        val found = kinds("The scheduler shall list upcoming events")
        assertTrue(found.contains(WrongCase), found.contains(MissingPeriod))
    ),
    suite("names")(
      test("each kind's name is its case name in kebab case, and names are distinct"):
        val kebab = (name: String) =>
          name.flatMap(c => if c.isUpper then s"-${c.toLower}" else c.toString).drop(1)
        val names = DiagnosticKind.values.toList.map(_.kebabName)
        assertTrue(
          names == DiagnosticKind.values.toList.map(kind => kebab(kind.toString)),
          names.distinct == names,
          WrongCase.kebabName == "wrong-case"
        )
    )
  )
