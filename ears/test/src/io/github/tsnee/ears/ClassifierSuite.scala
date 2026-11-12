package io.github.tsnee.ears

import Trees.*
import zio.test.*

object ClassifierSuite extends ZIOSpecDefault:

  private def tree(profile: Profile, input: String): Option[Requirement] =
    Requirement.classify(profile, input).toOption.map(_.stripSpans)

  private def classifiesTo(input: String, expected: Requirement) =
    assertTrue(tree(Profile.OpenSpec, input).contains(expected))

  private val profile = Gen.elements(Profile.values.toList*)

  def spec = suite("EARS classifier")(
    suite("templates")(
      test("ubiquitous"):
        classifiesTo("The checkout service SHALL display the order total.", ubiquitous)
      ,
      test("event-driven"):
        classifiesTo(
          "When a customer submits an order, the checkout service SHALL send a confirmation email.",
          eventDriven
        )
      ,
      test("state-driven"):
        classifiesTo(
          "While the cart is empty, the checkout service SHALL disable the pay button.",
          stateDriven
        )
      ,
      test("unwanted behavior"):
        classifiesTo(
          "If the inventory service is unreachable, then the checkout service SHALL show a stock warning.",
          unwanted
        )
      ,
      test("optional feature"):
        classifiesTo(
          "Where gift wrapping is enabled, the checkout service SHALL offer a gift message field.",
          optionalFeature
        )
      ,
      test("complex: state and event clauses"):
        classifiesTo(
          "While a user is signed in, when the session expires, the access service SHALL redirect the user to the sign-in page.",
          stateAndEvent
        )
      ,
      test("complex: feature, state, and unwanted-behavior clauses"):
        classifiesTo(
          "Where two-factor sign-in is enabled, while a user is signed in, if the device changes, then the access service SHALL request a second factor.",
          featureStateUnwanted
        )
    ),
    suite("profiles")(
      test("the classic profile accepts the lowercase keyword"):
        assertTrue(
          tree(Profile.Classic, "The scheduler shall list upcoming events.").contains(scheduler)
        )
    ),
    test("total classification: empty input gives at least one diagnostic and no tree"):
      assertTrue(Requirement.classify(Profile.OpenSpec, "").isLeft)
    ,
    test("round trip: rendering a tree and classifying it again gives an equal tree"):
      check(profile.flatMap(chosen => Generators.validRequirement(chosen).map((chosen, _)))) {
        case (chosen, (generated, rendered)) =>
          assertTrue(tree(chosen, rendered).contains(generated))
      }
  )
