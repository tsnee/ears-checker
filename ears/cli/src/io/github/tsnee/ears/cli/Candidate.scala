package io.github.tsnee.ears.cli

/** Where a requirement came from. Lines are 1-based. Line-based input reports no column: in plain
  * text it would only be the indentation, and Markdown parsers report positions in decoded text,
  * which drift from the source after escapes and entities.
  */
enum Location derives CanEqual:
  case Line(file: String, line: Int)
  case ChangeRequirement(change: String, capability: String, requirement: String)
  case SpecRequirement(spec: String, requirement: String)

/** A requirement statement found in the input, to be classified. */
final case class Candidate(location: Location, text: String) derives CanEqual

/** What kind of thing a warning is about, with what the user needs to know about it. */
enum WarningKind derives CanEqual:
  /** A table's body row has `cells` cells, more than its header's `headerCells`. */
  case TableExtraCells(cells: Int, headerCells: Int)

object WarningKind:

  extension (kind: WarningKind)
    /** "table-extra-cells": the kind's name in both output formats and the log. */
    def name: String = kind match
      case TableExtraCells(_, _) => "table-extra-cells"

    /** What the warning means, for people. */
    def message: String = kind match
      case TableExtraCells(cells, headerCells) =>
        s"This table row has $cells cells and its header $headerCells, so GitHub doesn't show " +
          s"the last ${cells - headerCells}."

/** Something the reader saw on a line that a user may want to know, though it isn't a finding: it
  * doesn't change the exit status.
  */
final case class Warning(location: Location.Line, kind: WarningKind) derives CanEqual

/** One input's requirements, to be classified, and its reader's warnings. */
final case class Found(candidates: List[Candidate], warnings: List[Warning]) derives CanEqual

object Candidate:

  /** Each nonblank line is one requirement. Blank means only whitespace in the Unicode sense, so a
    * line of ideographic spaces is blank too.
    */
  def fromPlainText(file: String, content: String): List[Candidate] =
    content.linesIterator.zipWithIndex.toList.collect {
      case (line, i) if !line.isBlank => Candidate(Location.Line(file, i + 1), line.strip)
    }

  /** Requirements from `openspec show --json` for a change (added and modified only) or a spec. */
  def fromOpenSpec(content: String): Either[String, List[Candidate]] =
    OpenSpecInput.candidates(content)
