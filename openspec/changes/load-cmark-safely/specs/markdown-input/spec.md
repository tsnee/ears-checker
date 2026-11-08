## MODIFIED Requirements

### Requirement: No library from the current directory
The Markdown reader on Scala Native SHALL load cmark only by an absolute path, apart from the bare name `libcmark.so` on Linux while `LD_LIBRARY_PATH` names only absolute directories.

#### Scenario: A library planted in a repository
- **WHEN** the reader runs natively on macOS in a directory that holds a file named `libcmark.dylib`, with Homebrew's cmark installed
- **THEN** it loads Homebrew's cmark from `/opt/homebrew/lib` and never opens the file in the current directory

#### Scenario: An empty entry in LD_LIBRARY_PATH
- **WHEN** the reader runs natively on Linux with `LD_LIBRARY_PATH` set to `:/opt/lib`, in a directory that holds a file named `libcmark.so`, with cmark installed in `/usr/lib/x86_64-linux-gnu`
- **THEN** it loads cmark by its absolute path in `/usr/lib/x86_64-linux-gnu` and never opens the file in the current directory

## ADDED Requirements

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
