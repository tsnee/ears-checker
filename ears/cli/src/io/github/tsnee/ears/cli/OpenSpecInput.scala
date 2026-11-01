package io.github.tsnee.ears.cli

import zio.json.*

/** Requirements from the JSON that `openspec show --json` prints for a change (with
  * `--deltas-only`) or for a spec. The two shapes differ by their `deltas` or `requirements` field.
  */
private[cli] object OpenSpecInput:

  private final case class Requirement(name: String, text: Option[String]) derives JsonDecoder
  private final case class Delta(spec: String, operation: String, requirement: Option[Requirement])
      derives JsonDecoder
  private final case class Change(id: String, deltas: List[Delta]) derives JsonDecoder
  private final case class Spec(id: String, requirements: List[Requirement]) derives JsonDecoder

  private val checkedOperations = Set("ADDED", "MODIFIED")

  def candidates(content: String): Either[String, List[Candidate]] =
    content.fromJson[Change].map(fromChange).orElse(content.fromJson[Spec].map(fromSpec)) match
      case Right(found) => Right(found)
      case Left(error)  => Left(s"Not OpenSpec change or spec JSON: $error")

  private def fromChange(change: Change): List[Candidate] =
    change.deltas.collect {
      case Delta(capability, operation, Some(requirement))
          if checkedOperations.contains(operation) =>
        Candidate(
          Location.ChangeRequirement(change.id, capability, requirement.name),
          requirement.text.getOrElse("")
        )
    }

  private def fromSpec(spec: Spec): List[Candidate] =
    spec.requirements.map { requirement =>
      Candidate(
        Location.SpecRequirement(spec.id, requirement.name),
        requirement.text.getOrElse("")
      )
    }
