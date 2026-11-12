package io.github.tsnee.ears

import Trees.*
import zio.test.*

object RenderSuite extends ZIOSpecDefault:

  private def renders(profile: Profile, requirement: Requirement, expected: String) =
    assertTrue(requirement.render(profile) == expected)

  def spec = suite("Canonical rendering")(
    suite("OpenSpec profile")(
      test("ubiquitous"):
        renders(Profile.OpenSpec, ubiquitous, "The checkout service SHALL display the order total.")
      ,
      test("event-driven"):
        renders(
          Profile.OpenSpec,
          eventDriven,
          "When a customer submits an order, the checkout service SHALL send a confirmation email."
        )
      ,
      test("state-driven"):
        renders(
          Profile.OpenSpec,
          stateDriven,
          "While the cart is empty, the checkout service SHALL disable the pay button."
        )
      ,
      test("unwanted behavior"):
        renders(
          Profile.OpenSpec,
          unwanted,
          "If the inventory service is unreachable, then the checkout service SHALL show a stock warning."
        )
      ,
      test("optional feature"):
        renders(
          Profile.OpenSpec,
          optionalFeature,
          "Where gift wrapping is enabled, the checkout service SHALL offer a gift message field."
        )
      ,
      test("complex: state and event"):
        renders(
          Profile.OpenSpec,
          stateAndEvent,
          "While a user is signed in, when the session expires, the access service SHALL redirect the user to the sign-in page."
        )
      ,
      test("complex: feature, state, and unwanted behavior"):
        renders(
          Profile.OpenSpec,
          featureStateUnwanted,
          "Where two-factor sign-in is enabled, while a user is signed in, if the device changes, then the access service SHALL request a second factor."
        )
    ),
    suite("classic profile")(
      test("ubiquitous"):
        renders(Profile.Classic, scheduler, "The scheduler shall list upcoming events.")
    )
  )
