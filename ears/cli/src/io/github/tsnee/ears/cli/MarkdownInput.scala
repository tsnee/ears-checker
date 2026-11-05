package io.github.tsnee.ears.cli

import io.github.tsnee.ears.markdown.Markdown

/** Requirement sentences in Markdown, from the Markdown facade (`ears/markdown`), which reads them
  * the same way on every platform. Only lines are reported: Markdown parsers give positions in
  * decoded text, which can't be mapped back to source columns after escapes and entities.
  */
private[cli] object MarkdownInput:

  def candidates(file: String, content: String): List[Candidate] =
    Markdown
      .sentences(content)
      .map(sentence => Candidate(Location.Line(file, sentence.line), sentence.text))
