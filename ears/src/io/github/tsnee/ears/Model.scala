package io.github.tsnee.ears

/** A half-open range `[start, end)` of UTF-16 code units in the classified input. */
final case class SourceSpan(start: Int, end: Int) derives CanEqual

/** A value together with the span of input it was read from. */
final case class Located[A](value: A, span: SourceSpan) derives CanEqual

/** The clause that starts the response: an event (When) or unwanted behavior (If). */
enum Trigger derives CanEqual:
  case Event(text: Located[String])
  case Unwanted(text: Located[String])

object Trigger:

  extension (trigger: Trigger)

    /** The clause text, whichever kind of trigger this is. */
    def text: Located[String] = trigger match
      case Event(text)    => text
      case Unwanted(text) => text

/** A requirement that matches an EARS template. The leading clauses always appear in the order
  * Where, While, then When or If, and at most one of When and If is present: the shape of the type
  * enforces both rules.
  */
final case class Requirement(
    feature: Option[Located[String]],
    precondition: Option[Located[String]],
    trigger: Option[Trigger],
    system: Located[String],
    response: Located[String]
) derives CanEqual

object Requirement:

  /** The requirement's syntax tree under `profile`, or every problem found in `input`. */
  def classify(
      profile: Profile,
      input: String
  ): Either[cats.data.NonEmptyList[Diagnostic], Requirement] =
    Classifier.classify(profile, input)

  extension (requirement: Requirement)

    /** Canonical text under `profile`; see `Rendering`. */
    def render(profile: Profile): String = Rendering.render(profile, requirement)

    /** The same requirement with every span set to the empty span at offset 0. */
    def stripSpans: Requirement =
      Requirement(
        requirement.feature.map(strip),
        requirement.precondition.map(strip),
        requirement.trigger.map {
          case Trigger.Event(text)    => Trigger.Event(strip(text))
          case Trigger.Unwanted(text) => Trigger.Unwanted(strip(text))
        },
        strip(requirement.system),
        strip(requirement.response)
      )

  private def strip(text: Located[String]): Located[String] = text.copy(span = SourceSpan(0, 0))

enum Template derives CanEqual:
  case Ubiquitous, EventDriven, StateDriven, UnwantedBehavior, OptionalFeature, Complex

object Template:

  /** No leading clause is ubiquitous, exactly one gives its own template, more is complex. */
  def of(requirement: Requirement): Template =
    (requirement.feature, requirement.precondition, requirement.trigger) match
      case (None, None, None)                      => Ubiquitous
      case (None, None, Some(Trigger.Event(_)))    => EventDriven
      case (None, None, Some(Trigger.Unwanted(_))) => UnwantedBehavior
      case (None, Some(_), None)                   => StateDriven
      case (Some(_), None, None)                   => OptionalFeature
      case _                                       => Complex

/** Which letter case the modal keyword must have. */
enum Profile(val keyword: String) derives CanEqual:
  case Classic extends Profile("shall")
  case OpenSpec extends Profile("SHALL")
