package io.github.tsnee.ears.cli

/** Why an input that was read couldn't be parsed in its format. Plain text always parses. */
enum ParseFailure derives CanEqual:
  /** Given as OpenSpec JSON, it isn't a change's or a spec's: the JSON parser's account. */
  case NotOpenSpec(detail: String)

  /** Markdown the parser itself couldn't read (out of memory, say): its account. */
  case UnreadMarkdown(detail: String)

object ParseFailure:

  extension (failure: ParseFailure)
    /** The message for standard error about the input named `source`, in the read errors' form. */
    def describe(source: String): String = failure match
      case NotOpenSpec(detail) =>
        s"Cannot parse $source: not OpenSpec change or spec JSON: $detail"
      case UnreadMarkdown(detail) => s"Cannot parse $source: $detail"

    /** The parser's own account, for the log, whose entry names the input already. */
    def reason: String = failure match
      case NotOpenSpec(detail)    => detail
      case UnreadMarkdown(detail) => detail
