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

object Candidate:

  /** Each nonblank line is one requirement. Blank means only whitespace in the Unicode sense, so a
    * line of ideographic spaces is blank too.
    */
  def fromPlainText(file: String, content: String): List[Candidate] =
    content.linesIterator.zipWithIndex.toList.collect {
      case (line, i) if !line.isBlank => Candidate(Location.Line(file, i + 1), line.strip)
    }

  /** Each sentence outside code, in a paragraph or list item, that contains the modal keyword. */
  def fromMarkdown(file: String, content: String): List[Candidate] =
    MarkdownInput.candidates(file, content)

  /** Requirements from `openspec show --json` for a change (added and modified only) or a spec. */
  def fromOpenSpec(content: String): Either[String, List[Candidate]] =
    OpenSpecInput.candidates(content)
