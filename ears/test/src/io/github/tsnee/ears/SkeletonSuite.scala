package io.github.tsnee.ears

import zio.test.*

object SkeletonSuite extends ZIOSpecDefault:

  private def anchored(input: String): Option[Shape.Anchored] =
    Skeleton.of(input).shape match
      case shape: Shape.Anchored => Some(shape)
      case _                     => None

  private def clauses(input: String): Option[List[(String, String, Boolean)]] =
    anchored(input).map(_.clauses.map(c => (c.keyword.value, c.text.value, c.comma.isDefined)))

  private def main(input: String): Option[(Option[String], String, String, String)] =
    anchored(input).map(a =>
      (a.article.map(_.value), a.system.value, a.modal.value, a.response.value)
    )

  private def spansOf(skeleton: Skeleton): List[SourceSpan] =
    val shapeSpans = skeleton.shape match
      case a: Shape.Anchored =>
        a.clauses.flatMap(c => List(c.keyword.span, c.text.span) ++ c.comma.toList) ++
          a.thenWord.toList ++ a.article.map(_.span).toList ++
          List(a.system.span, a.modal.span, a.response.span)
      case Shape.Unrecognized(span) => List(span)
      case Shape.NoModal            => Nil
    shapeSpans ++ skeleton.period.toList ++ skeleton.extraText.toList

  private def within(input: String)(span: SourceSpan): Boolean =
    0 <= span.start && span.start <= span.end && span.end <= input.length

  private val earsWords = Gen.elements(
    "When",
    "when",
    "While",
    "Where",
    "If",
    "if",
    "then",
    "the",
    "The",
    "SHALL",
    "shall",
    "should",
    "scheduler",
    "list",
    "events",
    ",",
    ".",
    ", ",
    ". ",
    " ",
    "10,000",
    "1.5",
    ""
  )

  def spec = suite("Skeleton parse")(
    test("ubiquitous: article, system, modal, response, and period"):
      val input = "The checkout service SHALL display the order total."
      val skeleton = Skeleton.of(input)
      assertTrue(
        clauses(input).contains(Nil),
        main(input).contains((Some("The"), "checkout service", "SHALL", "display the order total")),
        anchored(input).map(_.system.span).contains(SourceSpan(4, 20)),
        anchored(input).map(_.modal.span).contains(SourceSpan(21, 26)),
        anchored(input).map(_.response.span).contains(SourceSpan(27, 50)),
        skeleton.period.contains(SourceSpan(50, 51)),
        skeleton.extraText.isEmpty
      )
    ,
    test("event-driven clause with its comma"):
      val input =
        "When a customer submits an order, the checkout service SHALL send a confirmation email."
      assertTrue(
        clauses(input).contains(List(("When", "a customer submits an order", true))),
        main(input).contains(
          (Some("the"), "checkout service", "SHALL", "send a confirmation email")
        )
      )
    ,
    test("unwanted-behavior clause followed by then"):
      val input =
        "If the inventory service is unreachable, then the checkout service SHALL show a stock warning."
      assertTrue(
        clauses(input).contains(List(("If", "the inventory service is unreachable", true))),
        anchored(input).exists(_.thenWord.isDefined),
        main(input).contains((Some("the"), "checkout service", "SHALL", "show a stock warning"))
      )
    ,
    test("three leading clauses in input order"):
      val input =
        "Where two-factor sign-in is enabled, while a user is signed in, if the device changes, then the access service SHALL request a second factor."
      assertTrue(
        clauses(input).contains(
          List(
            ("Where", "two-factor sign-in is enabled", true),
            ("while", "a user is signed in", true),
            ("if", "the device changes", true)
          )
        ),
        main(input).contains((Some("the"), "access service", "SHALL", "request a second factor"))
      )
    ,
    test("source spans: the trigger spans offsets 5 to 17"):
      val input = "When a door opens, the access service SHALL log the event."
      assertTrue(
        anchored(input).flatMap(_.clauses.headOption).map(_.text.span).contains(SourceSpan(5, 17))
      )
    ,
    test("commas inside clause text stay in the clause"):
      val input =
        "When a user adds shoes, socks, and hats, the checkout service SHALL update the total."
      assertTrue(
        clauses(input).contains(List(("When", "a user adds shoes, socks, and hats", true)))
      )
    ,
    test("a keyword directly followed by a comma continues the clause instead of starting one"):
      val input =
        "When the order lists Where, While, and If, the checkout service SHALL save the order."
      assertTrue(
        clauses(input).contains(List(("When", "the order lists Where, While, and If", true)))
      )
    ,
    test("commas inside the response stay in the response"):
      val input = "The checkout service SHALL show the name, address, and phone number."
      assertTrue(
        main(input).map(_._4).contains("show the name, address, and phone number")
      )
    ,
    test("a clause without its comma ends at the last 'the' before the modal"):
      val input =
        "When a customer submits an order the checkout service SHALL send a confirmation email."
      assertTrue(
        clauses(input).contains(List(("When", "a customer submits an order", false))),
        main(input).contains(
          (Some("the"), "checkout service", "SHALL", "send a confirmation email")
        )
      )
    ,
    test("a system name without an article"):
      assertTrue(
        main("Scheduler SHALL list upcoming events.")
          .contains((None, "Scheduler", "SHALL", "list upcoming events"))
      )
    ,
    test("a weak modal anchors the main clause when no keyword is present"):
      assertTrue(
        main("The scheduler should list upcoming events.")
          .contains((Some("The"), "scheduler", "should", "list upcoming events"))
      )
    ,
    test("a weak modal inside clause text does not anchor the main clause"):
      assertTrue(
        Skeleton.of("When the order will ship the customer gets an email.").shape == Shape.NoModal
      )
    ,
    test("no modal word at all"):
      assertTrue(
        Skeleton.of("The checkout service displays the order total.").shape == Shape.NoModal
      )
    ,
    test("a keyword with nothing before it is unrecognized"):
      Skeleton.of("SHALL the scheduler list upcoming events.").shape match
        case Shape.Unrecognized(_) => assertCompletes
        case other                 => assertNever(s"expected Unrecognized, got $other")
    ,
    test("an empty response has an empty span right after the modal"):
      val input = "The scheduler SHALL."
      assertTrue(
        main(input).map(_._4).contains(""),
        anchored(input).map(_.response.span).contains(SourceSpan(19, 19))
      )
    ,
    test("no final period"):
      assertTrue(Skeleton.of("The scheduler SHALL list upcoming events").period.isEmpty)
    ,
    test("text after the first sentence"):
      val input = "The scheduler SHALL list upcoming events. It SHALL hide past events."
      assertTrue(
        Skeleton.of(input).extraText.contains(SourceSpan(42, 68)),
        main(input).map(_._4).contains("list upcoming events")
      )
    ,
    test("numbers and hyphens stay inside words"):
      assertTrue(
        main("The checkout service SHALL hold orders over 10,000 units for 1.5 days.")
          .map(_._4)
          .contains("hold orders over 10,000 units for 1.5 days")
      )
    ,
    test("totality: every span lies within the input, for any string"):
      check(Gen.string)(input => assertTrue(spansOf(Skeleton.of(input)).forall(within(input))))
    ,
    test("totality: every span lies within the input, for EARS-like word salad"):
      check(Gen.listOf(earsWords).map(_.mkString(" "))) { input =>
        assertTrue(spansOf(Skeleton.of(input)).forall(within(input)))
      }
  )
