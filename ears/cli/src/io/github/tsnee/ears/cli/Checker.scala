package io.github.tsnee.ears.cli

import cats.data.NonEmptyList
import io.github.tsnee.ears.Diagnostic
import io.github.tsnee.ears.Profile
import io.github.tsnee.ears.Requirement
import zio.json.*
import zio.json.ast.Json

/** What the program prints and its exit status: 0 when every requirement passes, 1 when any fails,
  * 2 when an input can't be read or parsed. `findings` counts what was reported, for the log.
  */
final case class Outcome(exitCode: Int, stdout: String, stderr: String, findings: Int)
    derives CanEqual

/** One diagnostic for one requirement, with the corrected sentence when it has a fix. */
final case class Finding(location: Location, requirement: String, diagnostic: Diagnostic)
    derives CanEqual

object Finding:

  extension (finding: Finding)
    def fixed: Option[String] = finding.diagnostic.fix.map(_.applyTo(finding.requirement))

object Checker:

  /** The outcome of checking `input`, which is the input text or why it couldn't be read. */
  def outcome(settings: Settings, input: Either[String, String]): Outcome =
    outcome(
      input.flatMap(candidates(settings, _)).map(found => NonEmptyList.one(settings -> found))
    )

  /** The outcome of checking every input's candidates, each under its own settings' profile, or of
    * the first input that couldn't be read or parsed. The output format is the first input's: it
    * comes from options shared by every input.
    */
  def outcome(found: Either[String, NonEmptyList[(Settings, List[Candidate])]]): Outcome =
    found match
      case Left(message) => Outcome(2, "", s"${Visible.escape(message)}\n", 0)
      case Right(inputs) =>
        val findings = inputs.toList.flatMap { (settings, candidates) =>
          candidates.flatMap(findingsFor(settings.profile, _))
        }
        val checked = inputs.toList.map((_, candidates) => candidates.size).sum
        val output = inputs.head match
          case (settings, _) => settings.output
        Outcome(
          if findings.isEmpty then 0 else 1,
          report(output, checked, findings),
          "",
          findings.size
        )

  /** The requirements in one input's content, or why the content can't be parsed. */
  def candidates(settings: Settings, content: String): Either[String, List[Candidate]] =
    settings.inputFormat match
      case InputFormat.PlainText    => Right(Candidate.fromPlainText(settings.sourceName, content))
      case InputFormat.Markdown     => Candidate.fromMarkdown(settings.sourceName, content)
      case InputFormat.OpenSpecJson => Candidate.fromOpenSpec(content)

  private def findingsFor(profile: Profile, candidate: Candidate): List[Finding] =
    Requirement.classify(profile, candidate.text) match
      case Left(diagnostics) =>
        diagnostics.toList.map(Finding(candidate.location, candidate.text, _))
      case Right(_) => Nil

  /** "wrong-case", the spelling the specs use: the library's name for the kind. */
  def kindName(finding: Finding): String = finding.diagnostic.kind.kebabName

  private def locationText(location: Location): String = location match
    case Location.Line(file, line)                                   => s"$file:$line"
    case Location.ChangeRequirement(change, capability, requirement) =>
      s"$change/$capability/\"$requirement\""
    case Location.SpecRequirement(spec, requirement) => s"$spec/\"$requirement\""

  /** Text output keeps one line per finding: text taken from the input (a corrected sentence, a
    * file or requirement name) has each whitespace run, line breaks included, shown as one space,
    * and any other character a terminal would act on shown as `\uXXXX` (`Visible`). JSON output
    * keeps the exact text.
    */
  private def singleLine(text: String): String =
    Visible.escape(
      text.map(c => if spacing(c) then ' ' else c).split(' ').filter(_.nonEmpty).mkString(" ")
    )

  /** Space, tab, line feed, vertical tab, form feed, and carriage return: Java's `\s`, spelled out,
    * since Scala Native's regex `\s` leaves out the vertical tab.
    */
  private def spacing(c: Char): Boolean = c == ' ' || (c >= '\t' && c <= '\r')

  private def report(output: OutputFormat, checked: Int, findings: List[Finding]): String =
    output match
      case OutputFormat.Text =>
        findings.map { finding =>
          val fix = finding.fixed.fold("")(sentence => s" Fix: \"${singleLine(sentence)}\"")
          val location = singleLine(locationText(finding.location))
          s"$location: ${kindName(finding)}: ${Visible.escape(finding.diagnostic.message)}$fix\n"
        }.mkString
      case OutputFormat.Json =>
        // The format version (docs/json-output.md): it changes only when a field is removed,
        // renamed, or changes meaning or type, or a location type is added. Then the release
        // that wrote it, since findings can change between releases within a format version.
        val document = Json.Obj(
          "version" -> Json.Num(1),
          "checker" -> Json.Str(CheckerBuild.version),
          "checked" -> Json.Num(checked),
          "findings" -> Json.Arr(findings.map(findingJson)*)
        )
        s"${document.toJson}\n"

  private def locationJson(location: Location): Json = location match
    case Location.Line(file, line) =>
      Json.Obj(
        "type" -> Json.Str("line"),
        "file" -> Json.Str(file),
        "line" -> Json.Num(line)
      )
    case Location.ChangeRequirement(change, capability, requirement) =>
      Json.Obj(
        "type" -> Json.Str("change"),
        "change" -> Json.Str(change),
        "capability" -> Json.Str(capability),
        "requirement" -> Json.Str(requirement)
      )
    case Location.SpecRequirement(spec, requirement) =>
      Json.Obj(
        "type" -> Json.Str("spec"),
        "spec" -> Json.Str(spec),
        "requirement" -> Json.Str(requirement)
      )

  private def findingJson(finding: Finding): Json =
    val span = finding.diagnostic.span
    Json.Obj(
      "location" -> locationJson(finding.location),
      "requirement" -> Json.Str(finding.requirement),
      "kind" -> Json.Str(kindName(finding)),
      "message" -> Json.Str(finding.diagnostic.message),
      "span" -> Json.Obj("start" -> Json.Num(span.start), "end" -> Json.Num(span.end)),
      "fix" -> finding.fixed.fold[Json](Json.Null)(Json.Str(_))
    )
