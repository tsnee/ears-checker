package io.github.tsnee.ears.cli

import zio.json.*
import zio.json.ast.Json

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

  /** The requirements to check, or the JSON parser's account of what's wrong: the shape is the
    * change's when the text is an object with `deltas` and the spec's otherwise, so a broken change
    * is reported against the change's fields, not the spec's. The text is then parsed as that shape
    * directly, whose account of malformed JSON says where it went wrong (`(expected '"' got 'n')`),
    * where the generic tree's says less.
    */
  def candidates(content: String): Either[String, List[Candidate]] =
    if content.fromJson[Json.Obj].exists(_.fields.exists((key, _) => key == "deltas")) then
      content.fromJson[Change].map(fromChange)
    else content.fromJson[Spec].map(fromSpec)

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
