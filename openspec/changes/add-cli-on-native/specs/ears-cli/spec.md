## ADDED Requirements

### Requirement: Same results on Scala Native
The EARS checker SHALL write the same standard output and exit with the same status on Scala Native as on the JVM, for the same arguments and input.

#### Scenario: A failing Markdown file
- **WHEN** the checker runs as `ears-check --output json notes.md` natively and on the JVM, and `notes.md` has a requirement with the wrong modal case
- **THEN** both write the same JSON to standard output and both exit with status 1

### Requirement: Same messages on Scala Native
The EARS checker SHALL write the same messages to standard error on Scala Native as on the JVM, apart from log entries.

#### Scenario: A missing file
- **WHEN** the checker runs as `ears-check missing.txt` natively and on the JVM
- **THEN** both write `Cannot read missing.txt` to standard error and both exit with status 2

### Requirement: Missing cmark
If the native checker must read a Markdown input and finds no cmark library of release 0.30 or later, then the EARS checker SHALL write a message naming the cmark packages to install to standard error and exit with status 2.

#### Scenario: No cmark installed
- **WHEN** the native checker runs as `ears-check notes.md` on a system with no cmark library
- **THEN** it writes a message that names `brew install cmark` and `apt install cmark` to standard error and exits with status 2

#### Scenario: Plain text without cmark
- **WHEN** the native checker runs as `ears-check notes.txt` on a system with no cmark library
- **THEN** it checks `notes.txt` as it would with cmark installed

### Requirement: Libraries tried for Markdown
If the native checker finds no usable cmark library for a Markdown input, then the EARS checker SHALL list each library it tried, and why it couldn't use it, in that input's error log entry.

#### Scenario: No cmark installed, in the log
- **WHEN** the native checker runs as `ears-check notes.md` on Linux with no cmark library
- **THEN** its error log entry for `notes.md` has an `attempts` detail naming `libcmark.so` and the loader's error for it
