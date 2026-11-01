package io.github.tsnee.ears

import java.util.Locale

private[ears] object Rendering:

  /** Canonical text for a requirement: the first word capitalized, later clause keywords in
    * lowercase, `, then` after an If clause, the profile's keyword, and a final period.
    */
  def render(profile: Profile, requirement: Requirement): String =
    val clauses = List(
      requirement.feature.map(feature => s"where ${feature.value}, "),
      requirement.precondition.map(precondition => s"while ${precondition.value}, "),
      requirement.trigger.map {
        case Trigger.Event(text)    => s"when ${text.value}, "
        case Trigger.Unwanted(text) => s"if ${text.value}, then "
      }
    ).flatten
    val main =
      s"the ${requirement.system.value} ${profile.keyword} ${requirement.response.value}."
    capitalize(clauses.mkString + main)

  private def capitalize(sentence: String): String =
    sentence.take(1).toUpperCase(Locale.ROOT) + sentence.drop(1)
