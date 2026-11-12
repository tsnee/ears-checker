package io.github.tsnee.ears

import Trees.*
import zio.test.*

object ModelSuite extends ZIOSpecDefault:

  def spec = suite("Data model")(
    suite("template derivation")(
      test("no leading clause is ubiquitous"):
        assertTrue(Template.of(ubiquitous) == Template.Ubiquitous)
      ,
      test("a When clause alone is event-driven"):
        assertTrue(Template.of(eventDriven) == Template.EventDriven)
      ,
      test("a While clause alone is state-driven"):
        assertTrue(Template.of(stateDriven) == Template.StateDriven)
      ,
      test("an If clause alone is unwanted behavior"):
        assertTrue(Template.of(unwanted) == Template.UnwantedBehavior)
      ,
      test("a Where clause alone is an optional feature"):
        assertTrue(Template.of(optionalFeature) == Template.OptionalFeature)
      ,
      test("While and When together are complex"):
        assertTrue(Template.of(stateAndEvent) == Template.Complex)
      ,
      test("Where and While together, with no trigger, are complex"):
        val featureAndState = Requirement(
          feature = Some(text("gift wrapping is enabled")),
          precondition = Some(text("the cart is not empty")),
          trigger = None,
          system = text("checkout service"),
          response = text("offer a gift message field")
        )
        assertTrue(Template.of(featureAndState) == Template.Complex)
      ,
      test("Where, While, and If together are complex"):
        assertTrue(Template.of(featureStateUnwanted) == Template.Complex)
    ),
    suite("spans")(
      test("stripSpans replaces every span with the empty span at offset 0"):
        val spanned = Requirement(
          Some(Located("f", SourceSpan(6, 7))),
          Some(Located("p", SourceSpan(15, 16))),
          Some(Trigger.Event(Located("t", SourceSpan(23, 24)))),
          Located("s", SourceSpan(30, 31)),
          Located("r", SourceSpan(38, 39))
        )
        assertTrue(
          spanned.stripSpans == Requirement(
            Some(text("f")),
            Some(text("p")),
            Some(Trigger.Event(text("t"))),
            text("s"),
            text("r")
          )
        )
    )
  )
