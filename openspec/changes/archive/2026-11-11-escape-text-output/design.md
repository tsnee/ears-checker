# Design

## Context

Observations (2026-11-11, at 136b28c):

- **Text output** (`Checker.report`, `ears/cli/src/.../Checker.scala`): each finding is `<location>: <kind>: <message>[ Fix: "<sentence>"]`. `singleLine` collapses the regex `\s+` to one space in the location and the fix; nothing else is touched. On the JVM and Node `\s` is space, tab, LF, VT, FF, and CR; Scala Native's (nativelib 0.5.12, a port of re2j) leaves out VT, so the native binary printed a VT raw where the jar printed a space (code review, confirmed with both builds, 2026-11-11). `kindName` is the library's own kebab-case name; `message` comes from the classifier and can quote words from the requirement.
- **Reproduced** with the native binary (shared code, so every build): a plain-text requirement with `ESC [2K ESC [1A` and a missing comma printed both sequences raw in its `Fix:` (the security review; rechecked here with `od -c`). A missing file named with `ESC [1A` printed `Cannot read mis ESC [1A sing.txt` raw on standard error.
- **Messages on standard error** that aren't log lines: `Outcome.stderr` (`Checker.scala:39`, the per-input message), and the usage and argument errors (`Cli.scala:101,107`, decline's text).
- **Log lines** are already safe: `Logging.escape` writes backslash as `\\`, LF and CR as `\n` and `\r`, U+2028 and U+2029 and every ISO control (`Character.isISOControl`: U+0000–U+001F and U+007F–U+009F, which includes NEL U+0085 and CSI U+009B) as `\uXXXX` (lowercase hex).
- **JSON output** is safe: zio-json escapes control characters (security review).
- **Spec:** `ears-cli`'s "Text output" says one human-readable line per finding; nothing about characters. SECURITY.md lists escaping problems in scope.

## Goals / Non-Goals

**Goals:**

- No control character or line separator from the input reaches the terminal raw, in text output or in plain messages.
- Text output stays one line per finding, and readable.

**Non-Goals:**

- Unicode format characters such as bidirectional overrides (U+202E): they aren't control characters, terminals rarely act on them, and the review didn't raise them.
- Changing JSON output or log lines.

## Decisions

### 1. One pure function, `Visible`, shared by the text report and the messages

- `Visible.escape(text)` writes each ISO control, U+2028, and U+2029 as `\uXXXX`, the form the log lines use; it lives in `ears/cli/src`, so every build has it and the JVM measures it.
- The text report applies it after `singleLine` to the location and the fix, and to the message, so a run of spaces, tabs, LFs, VTs, FFs, and CRs is still one space and anything left (NEL, ESC, BEL, C1) becomes visible. `singleLine` tests those six characters itself instead of using a regex, so every build collapses the same ones.
- `Outcome`'s messages go through it whole, line feeds included: none has a line break of its own, and a line feed in a file name could otherwise forge a line (code review, confirmed with the native binary). decline's argument errors, which quote the arguments, go through it one by one (`Help.fromCommand(command).withErrors(...)`), while its usage text keeps its own lines.

### 2. Backslashes stay

- Unlike the log lines, text output doesn't double a backslash. Its readers are people, and a `Fix:` sentence is meant to be copied back into the file; requirements with backslashes would otherwise come out changed. The cost: a requirement that literally contains `\u001b` looks the same as an escaped ESC. Tools that must tell them apart use JSON output, which is exact. The owner chose this over doubling backslashes (2026-11-11), after examples from the native binary: `C:\orders\new` in a `Fix:` would print as `C:\\orders\\new` if doubled.

## Risks / Trade-offs

- **[Visible change]** Text output and messages change only for input with control characters, which a requirement never has on purpose; the CHANGELOG's Fixed section says so.
- **[Format characters]** Bidirectional overrides still pass through (non-goal); a later change can add them to `Visible` if they matter.

## Open Questions

- None.
