package io.github.tsnee.ears

/** Syntax trees for the spec scenarios, with placeholder spans. Compare with `stripSpans`. */
object Trees:

  def text(value: String): Located[String] = Located(value, SourceSpan(0, 0))

  val ubiquitous: Requirement =
    Requirement(None, None, None, text("checkout service"), text("display the order total"))

  val eventDriven: Requirement = Requirement(
    None,
    None,
    Some(Trigger.Event(text("a customer submits an order"))),
    text("checkout service"),
    text("send a confirmation email")
  )

  val stateDriven: Requirement = Requirement(
    None,
    Some(text("the cart is empty")),
    None,
    text("checkout service"),
    text("disable the pay button")
  )

  val unwanted: Requirement = Requirement(
    None,
    None,
    Some(Trigger.Unwanted(text("the inventory service is unreachable"))),
    text("checkout service"),
    text("show a stock warning")
  )

  val optionalFeature: Requirement = Requirement(
    Some(text("gift wrapping is enabled")),
    None,
    None,
    text("checkout service"),
    text("offer a gift message field")
  )

  val stateAndEvent: Requirement = Requirement(
    None,
    Some(text("a user is signed in")),
    Some(Trigger.Event(text("the session expires"))),
    text("access service"),
    text("redirect the user to the sign-in page")
  )

  val featureStateUnwanted: Requirement = Requirement(
    Some(text("two-factor sign-in is enabled")),
    Some(text("a user is signed in")),
    Some(Trigger.Unwanted(text("the device changes"))),
    text("access service"),
    text("request a second factor")
  )

  val scheduler: Requirement =
    Requirement(None, None, None, text("scheduler"), text("list upcoming events"))
