package io.github.tsnee.ears.markdown

/** A document's lines, ended by `\r\n`, `\r`, or `\n`, as CommonMark ends them. */
private[markdown] object Lines:

  /** The offset each line starts at: 0, then after each `\r\n`, `\r`, or `\n`. */
  def starts(content: String): Vector[Int] =
    0 +: content.indices
      .filter { i =>
        content.charAt(i) == '\n' || (content.charAt(i) == '\r' &&
          (i + 1 >= content.length || content.charAt(i + 1) != '\n'))
      }
      .map(_ + 1)
      .toVector

  /** Each line's text, without its line ending; the text after the last ending is a line too. */
  def of(content: String): Vector[String] =
    val from = starts(content)
    from.zip(from.drop(1) :+ (content.length + 1)).map { (start, next) =>
      content.substring(start, next - 1).stripSuffix("\r")
    }
