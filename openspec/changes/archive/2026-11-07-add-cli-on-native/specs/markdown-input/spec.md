## ADDED Requirements

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

The Markdown reader on Scala Native SHALL load cmark only by an absolute path, apart from the bare name `libcmark.so` on Linux, which the system loader resolves from its own library directories.

#### Scenario: A library planted in a repository

- **WHEN** the reader runs natively on macOS in a directory that holds a file named `libcmark.dylib`, with Homebrew's cmark installed
- **THEN** it loads Homebrew's cmark from `/opt/homebrew/lib` and never opens the file in the current directory
