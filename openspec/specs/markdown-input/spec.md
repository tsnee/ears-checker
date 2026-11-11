# markdown-input Specification

## Purpose
Reads requirement sentences from Markdown documents for the EARS checker, with the same
sentences and lines on every platform the checker builds for.

## Requirements

### Requirement: Sentences in paragraphs and list items
When the Markdown reader reads a document, the Markdown reader SHALL return each sentence of each paragraph, list items included, that contains the modal keyword outside inline code.

#### Scenario: List item
- **WHEN** the reader reads `# Reminders` followed by a list item `- The scheduler SHALL list upcoming events.` and a fenced code block containing `SHALL inside code`
- **THEN** it returns only `The scheduler SHALL list upcoming events.`

#### Scenario: Keyword only in inline code
- **WHEN** the reader reads the paragraph ``Write `SHALL` in uppercase.``
- **THEN** it returns no sentence

### Requirement: Sentence boundaries
The Markdown reader SHALL end a sentence at a period outside inline code that is followed by whitespace, with each line break inside a paragraph read as a space.

#### Scenario: Two sentences across a soft line break
- **WHEN** the reader reads the paragraph `Reminders are useful. When a reminder is due,` followed on the next line by `the scheduler SHALL send a notice.`
- **THEN** it returns `When a reminder is due, the scheduler SHALL send a notice.`

#### Scenario: A period inside inline code
- **WHEN** the reader reads the paragraph ``The checker SHALL write `Cannot read notes.txt. Check the path.` on failure.``
- **THEN** it returns the paragraph as one sentence

### Requirement: Sentence lines
The Markdown reader SHALL give each sentence the 1-based line of the source on which the sentence starts.

#### Scenario: Escapes and entities earlier in the paragraph
- **WHEN** the reader reads `Some \*escaped\* text &amp;amp; more.` followed on line 2 by `The scheduler shall list events.`
- **THEN** it returns that sentence on line 2

#### Scenario: Sentence starting with inline code
- **WHEN** the reader reads a document whose line 7 is the list item ``- `ears-checker` shall check every line.``
- **THEN** it returns `ears-checker shall check every line.` on line 7

### Requirement: Same results on every platform
While a document nests no deeper than every platform's parser follows, the Markdown reader SHALL return the same sentences, with the same lines, on the JVM, on Node, and on Scala Native.

#### Scenario: Conformance suite
- **WHEN** the conformance suite runs its fixtures on the JVM, on Node, and on Scala Native
- **THEN** each fixture gives the same sentences and lines on all three

#### Scenario: Constructs one parser misplaces
- **WHEN** the reader reads `[r]: /u` followed on line 2 by `The scheduler shall list.`, on each platform
- **THEN** it returns that sentence on line 2 on all three, though cmark itself places the paragraph on line 1

### Requirement: Deep nesting
If a document nests constructs thousands of levels deep, then the Markdown reader SHALL return its sentences without failing.

#### Scenario: Ten thousand nested block quotes
- **WHEN** the reader reads 10,000 nested block quotes around `The scheduler shall list.`, on each platform
- **THEN** it returns at most one sentence and doesn't fail, though the platforms may differ on whether they return it

### Requirement: Inline code in sentences
The Markdown reader SHALL return each inline code span in a sentence as a code span, between backticks.

#### Scenario: A quoted file name
- **WHEN** the reader reads the paragraph ``The importer SHALL skip `notes.md` files.``
- **THEN** it returns ``The importer SHALL skip `notes.md` files.``

#### Scenario: Escaped backticks
- **WHEN** the reader reads the paragraph ``The shell SHALL print \`now\` on start.``
- **THEN** it returns ``The shell SHALL print \`now\` on start.``, whose backticks quote nothing

#### Scenario: A backslash before code
- **WHEN** the reader reads the paragraph ``The exporter SHALL write to C:\\`out. Then` daily.``, an escaped backslash and then code
- **THEN** it returns ``The exporter SHALL write to C:\\`out. Then` daily.``, the backslash doubled so the code stays code, and other backslashes single

#### Scenario: Code that holds a backtick
- **WHEN** the reader reads the paragraph ```The formatter SHALL escape ``a ` b`` in output.```
- **THEN** it returns a sentence that quotes ``a ` b`` between double backticks

### Requirement: cmark found at run time
When the Markdown reader on Scala Native first reads a document, the Markdown reader SHALL load an installed cmark library of release 0.30 or later, whatever its exact release.

#### Scenario: Homebrew's cmark
- **WHEN** the reader runs natively on macOS with Homebrew's cmark installed in `/opt/homebrew/lib`
- **THEN** it loads that cmark and reads the document

#### Scenario: Ubuntu's runtime package only
- **WHEN** the reader runs natively on Ubuntu with only cmark's runtime package `libcmark0.30.2` installed, which has `libcmark.so.0.30.2` and no `libcmark.so`
- **THEN** it loads `libcmark.so.0.30.2` and reads the document

#### Scenario: A newer release
- **WHEN** the reader runs natively where the installed cmark is a release newer than any the conformance suite has run on
- **THEN** it loads that cmark and reads the document

#### Scenario: An older release earlier in the search
- **WHEN** the reader runs natively on Linux where `/usr/local/lib/libcmark.so` is cmark 0.29 and `/usr/lib/x86_64-linux-gnu/libcmark.so.0.30.2` is installed
- **THEN** it loads `libcmark.so.0.30.2` and reads the document

### Requirement: No library from the current directory
The Markdown reader on Scala Native SHALL load cmark only by an absolute path, apart from the bare name `libcmark.so` on Linux while `LD_LIBRARY_PATH` names only absolute directories.

#### Scenario: A library planted in a repository
- **WHEN** the reader runs natively on macOS in a directory that holds a file named `libcmark.dylib`, with Homebrew's cmark installed
- **THEN** it loads Homebrew's cmark from `/opt/homebrew/lib` and never opens the file in the current directory

#### Scenario: An empty entry in LD_LIBRARY_PATH
- **WHEN** the reader runs natively on Linux with `LD_LIBRARY_PATH` set to `:/opt/lib`, in a directory that holds a file named `libcmark.so`, with cmark installed in `/usr/lib/x86_64-linux-gnu`
- **THEN** it loads cmark by its absolute path in `/usr/lib/x86_64-linux-gnu` and never opens the file in the current directory

### Requirement: Relative library path on macOS
If `DYLD_LIBRARY_PATH` has a relative directory entry, then the Markdown reader on Scala Native on macOS SHALL decline to load cmark, with a message naming `DYLD_LIBRARY_PATH`.

#### Scenario: The current directory in DYLD_LIBRARY_PATH
- **WHEN** the native binary reads a Markdown file on macOS with `DYLD_LIBRARY_PATH` set to `.`, in a directory that holds a file named `libcmark.dylib`
- **THEN** it exits with status 2 and a message naming `DYLD_LIBRARY_PATH`, and never opens the file in the current directory

#### Scenario: An empty entry in DYLD_LIBRARY_PATH
- **WHEN** the native binary reads a Markdown file on macOS with `DYLD_LIBRARY_PATH` set to `:/opt/homebrew/lib`, in a directory that holds a file named `libcmark.dylib`
- **THEN** it loads cmark from `/opt/homebrew/lib`, since macOS ignores the empty entry

#### Scenario: Plain text with the current directory in DYLD_LIBRARY_PATH
- **WHEN** the native binary reads a plain-text file on macOS with `DYLD_LIBRARY_PATH` set to `.`
- **THEN** it checks the file as usual
