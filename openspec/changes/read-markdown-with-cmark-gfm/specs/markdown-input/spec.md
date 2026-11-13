## ADDED Requirements

### Requirement: GitHub's parser

The Markdown reader SHALL read a document's blocks and inline text as cmark-gfm 0.29.0.gfm.13 with its table extension reads them.

#### Scenario: A link definition directly above a table

- **WHEN** the reader reads `[s]: https://example.com/scheduler`, `| ID | Requirement |`, `|---|---|`, and `| A | [The scheduler][s] shall list events. |` with no blank line between them
- **THEN** it returns `[The scheduler][s] shall list events.` on line 4, since cmark-gfm, like GitHub, reads the first line as paragraph text and not as a definition

### Requirement: No Markdown library installed

The Markdown reader SHALL read Markdown on a machine with no Markdown library installed.

#### Scenario: The native binary on a bare Mac

- **WHEN** the native binary reads a Markdown file on macOS with neither cmark nor cmark-gfm installed
- **THEN** it returns the file's sentences

#### Scenario: The native binary on a bare Linux

- **WHEN** the native binary reads a Markdown file on Ubuntu 22.04 with no cmark package installed
- **THEN** it returns the file's sentences

### Requirement: Many tables in a row

If a document holds thousands of tables, each starting on the line after the previous one's last line, then the Markdown reader SHALL return their sentences without failing.

#### Scenario: Tables separated by indented code

- **WHEN** the reader reads 1,600 repetitions of `The system SHALL a|b`, `-|-`, and `    code`, on each platform
- **THEN** it returns 1,600 sentences `The system SHALL a`, one from each table's header row

## REMOVED Requirements

### Requirement: cmark found at run time

**Reason**: The native binary compiles cmark-gfm in and loads no Markdown library when it runs.

**Migration**: None; the native binary reads Markdown with nothing installed, and an installed cmark is no longer used.

### Requirement: No library from the current directory

**Reason**: The native binary loads no Markdown library, so no library in the current directory can be loaded in its place.

**Migration**: None.

### Requirement: Relative library path on macOS

**Reason**: The native binary loads no Markdown library, so `DYLD_LIBRARY_PATH` no longer affects how it reads Markdown.

**Migration**: None; a relative entry in `DYLD_LIBRARY_PATH` no longer stops the native binary from reading Markdown.
