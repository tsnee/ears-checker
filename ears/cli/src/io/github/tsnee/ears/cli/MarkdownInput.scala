package io.github.tsnee.ears.cli

import io.github.tsnee.ears.markdown.Markdown
import io.github.tsnee.ears.markdown.MarkdownWarning

/** Requirement sentences in Markdown, from the Markdown facade (`ears/markdown`), which reads them
  * the same way on every platform. Only lines are reported: Markdown parsers give positions in
  * decoded text, which can't be mapped back to source columns after escapes and entities.
  */
private[cli] object MarkdownInput:

  /** The candidates and the reader's warnings, each located in `file`; or why the Markdown couldn't
    * be read (only on Scala Native, without cmark).
    */
  def read(file: String, content: String): Either[String, Found] =
    Markdown
      .read(content)
      .map(text =>
        Found(
          text.sentences
            .map(sentence => Candidate(Location.Line(file, sentence.line), sentence.text)),
          text.warnings.map(warning(file, _))
        )
      )

  /** The facade's warning as the CLI's, located on its line in `file`. */
  private def warning(file: String, seen: MarkdownWarning): Warning = seen match
    case MarkdownWarning.ExtraCells(line, cells, headerCells) =>
      Warning(
        Location.Line(file, line),
        WarningKind.TableExtraCells(cells = cells, headerCells = headerCells)
      )
