## ADDED Requirements

### Requirement: OpenSpec Markdown inferred from the path

While no input format is selected, when the input's path is a file named spec.md below the specs directory of a directory named openspec or of a change in that directory's changes, the EARS checker SHALL read the input as OpenSpec Markdown.

#### Scenario: A spec

- **WHEN** the checker is given "openspec/specs/scheduling/spec.md" with no format option
- **THEN** it reads the file as OpenSpec Markdown

#### Scenario: A change's delta spec

- **WHEN** the checker is given "openspec/changes/add-reminders/specs/scheduling/spec.md" with no format option
- **THEN** it reads the file as OpenSpec Markdown

#### Scenario: An archived change's delta spec

- **WHEN** the checker is given "openspec/changes/archive/2026-11-17-add-reminders/specs/scheduling/spec.md" with no format option
- **THEN** it reads the file as OpenSpec Markdown

#### Scenario: A nested capability

- **WHEN** the checker is given "project/openspec/specs/billing/invoices/spec.md" with no format option
- **THEN** it reads the file as OpenSpec Markdown

#### Scenario: A change's proposal

- **WHEN** the checker is given "openspec/changes/add-reminders/proposal.md" with no format option
- **THEN** it reads the file as Markdown

#### Scenario: A spec.md outside OpenSpec

- **WHEN** the checker is given "docs/specs/scheduling/spec.md" with no format option
- **THEN** it reads the file as Markdown

### Requirement: OpenSpec Markdown format option

When the input format openspec-markdown is selected, the EARS checker SHALL read the input as OpenSpec Markdown.

#### Scenario: A spec outside its layout

- **WHEN** the checker is given "scheduling.md" with the option `--format openspec-markdown`
- **THEN** it reads the file as OpenSpec Markdown

### Requirement: Requirements in OpenSpec Markdown

When the EARS checker reads OpenSpec Markdown, the EARS checker SHALL check the text of each requirement in the Requirements section and in the ADDED Requirements and MODIFIED Requirements sections, as one requirement each.

#### Scenario: A spec's requirements

- **WHEN** the checker reads OpenSpec Markdown whose Requirements section holds two requirements
- **THEN** it checks two requirements

#### Scenario: A delta spec's sections

- **WHEN** the checker reads OpenSpec Markdown with one requirement under ADDED Requirements, one under MODIFIED Requirements, one under REMOVED Requirements, and a rename under RENAMED Requirements
- **THEN** it checks the added and the modified requirement, and nothing else

#### Scenario: Section headings in any case

- **WHEN** the checker reads OpenSpec Markdown with a requirement under `## Added Requirements`
- **THEN** it checks that requirement

### Requirement: Text of a requirement in OpenSpec Markdown

When the EARS checker reads a requirement in OpenSpec Markdown, the EARS checker SHALL take as its text the nonblank lines between its heading and the next heading, outside fenced code blocks and other than lines that start with a bold label, each trimmed and joined by line breaks.

#### Scenario: A statement on two lines

- **WHEN** a requirement's heading is followed by "The scheduler SHALL list" and "upcoming events." on two lines, then a scenario heading
- **THEN** the checker checks "The scheduler SHALL list" and "upcoming events." joined by a line break as one requirement

#### Scenario: A fenced example

- **WHEN** a requirement's statement is followed by a fenced code block before its first scenario
- **THEN** the checker checks the statement without the code block's lines

#### Scenario: A label line

- **WHEN** a requirement's statement is followed by the line `**ID**: SCHED-1`
- **THEN** the checker checks the statement without that line

#### Scenario: Only label lines

- **WHEN** a requirement's only text is the line `**Constraint**: The scheduler SHALL list upcoming events.`
- **THEN** the checker checks that line as the requirement

#### Scenario: No text under the heading

- **WHEN** a spec's requirement heading is `### The scheduler SHALL list upcoming events.` with only scenarios under it
- **THEN** the checker checks the heading's title as the requirement

#### Scenario: No text under a requirement heading

- **WHEN** an added requirement's heading is `### Requirement: The scheduler SHALL list events` with only scenarios under it
- **THEN** the checker checks "Requirement: The scheduler SHALL list events" as the requirement, as OpenSpec's JSON gives it

### Requirement: Text outside the requirements in OpenSpec Markdown

When the EARS checker reads OpenSpec Markdown, the EARS checker SHALL check no text other than its requirements' text.

#### Scenario: Scenarios

- **WHEN** the checker reads OpenSpec Markdown whose only requirement passes and whose scenario says "**WHEN** the scheduler starts"
- **THEN** it reports no findings

#### Scenario: A purpose

- **WHEN** the checker reads OpenSpec Markdown whose Purpose section says "The scheduler lists events."
- **THEN** it reports no finding for that sentence

#### Scenario: A removed requirement's reason

- **WHEN** the checker reads OpenSpec Markdown whose REMOVED Requirements section gives a requirement the reason "Replaced by reminders."
- **THEN** it reports no finding for that reason

### Requirement: Locations in OpenSpec Markdown

When the EARS checker reports a finding for a requirement in OpenSpec Markdown, the EARS checker SHALL locate the finding at the line where the requirement's text starts.

#### Scenario: Text after a blank line

- **WHEN** a requirement's heading is on line 10 of "spec.md", line 11 is blank, and its failing statement is on line 12
- **THEN** the finding is located at "spec.md:12"

#### Scenario: The heading as the text

- **WHEN** a spec's requirement heading on line 10 of "spec.md" is a failing statement with only scenarios under it
- **THEN** the finding is located at "spec.md:10"

### Requirement: Files in an openspec directory

When the EARS checker checks a directory named openspec, or a directory below its specs or changes directory, the EARS checker SHALL check only the OpenSpec Markdown spec files in it.

#### Scenario: A project's openspec directory

- **WHEN** the checker is given "project", which holds "README.md" and an "openspec" directory with "config.yaml", "specs/scheduling/spec.md", and a change "add-reminders" with "proposal.md", "design.md", "tasks.md", and "specs/scheduling/spec.md"
- **THEN** it checks "project/README.md" as Markdown and the two spec.md files as OpenSpec Markdown, and reads nothing else

#### Scenario: A change's directory

- **WHEN** the checker is given "openspec/changes/add-reminders", which holds "proposal.md" and "specs/scheduling/spec.md"
- **THEN** it checks the spec.md file and doesn't read "proposal.md"

#### Scenario: Another directory below one named openspec

- **WHEN** the checker is given "src/openspec/docs", which holds "guide.md"
- **THEN** it checks "guide.md" as Markdown

### Requirement: Archived changes in a directory walk

When the EARS checker finds the archive directory of an openspec directory's changes in a directory, the EARS checker SHALL skip the archive directory.

#### Scenario: A walk over openspec

- **WHEN** the checker is given "openspec", which holds "specs/scheduling/spec.md" and "changes/archive/2026-11-17-add-reminders/specs/scheduling/spec.md"
- **THEN** it checks "openspec/specs/scheduling/spec.md" and not the archived one

#### Scenario: The archive given itself

- **WHEN** the checker is given "openspec/changes/archive", which holds "2026-11-17-add-reminders/specs/scheduling/spec.md"
- **THEN** it checks that spec.md file as OpenSpec Markdown

## MODIFIED Requirements

### Requirement: Markdown inferred from file name

While no input format is selected, when the input file name ends in .md and its path isn't an OpenSpec Markdown spec file, the EARS checker SHALL read the input as Markdown.

#### Scenario: Markdown file name

- **WHEN** the checker is given the file "requirements.md" with no format option
- **THEN** it reads the file as Markdown

#### Scenario: An OpenSpec spec's path

- **WHEN** the checker is given "openspec/specs/scheduling/spec.md" with no format option
- **THEN** it reads the file as OpenSpec Markdown, not as Markdown

### Requirement: Default profile for OpenSpec input

While no profile is selected, when the EARS checker reads OpenSpec JSON or OpenSpec Markdown, the EARS checker SHALL use the OpenSpec profile.

#### Scenario: Uppercase keyword passes by default

- **WHEN** the checker reads OpenSpec JSON whose only requirement is "The scheduler SHALL list upcoming events." with no profile option
- **THEN** it reports no findings

#### Scenario: OpenSpec Markdown

- **WHEN** the checker reads OpenSpec Markdown whose only requirement is "The scheduler SHALL list upcoming events." with no profile option
- **THEN** it reports no findings

### Requirement: Directory input

When the EARS checker is given a directory, the EARS checker SHALL check every file whose name ends in .md or .txt in the directory and in its subdirectories, other than those in a directory named openspec or below its specs or changes directory.

#### Scenario: Files in a subdirectory

- **WHEN** the checker is given "docs", which holds "scheduling.md" and a subdirectory "billing" that holds "invoices.md"
- **THEN** it checks the requirements in both files

#### Scenario: Markdown and plain text

- **WHEN** the checker is given "docs", which holds "scheduling.md" and "billing.txt", with no format option
- **THEN** it reads "scheduling.md" as Markdown and "billing.txt" as plain text

#### Scenario: Other files left alone

- **WHEN** the checker is given "docs", which holds "scheduling.md", "logo.png", and "package.json"
- **THEN** it reads "scheduling.md" and neither of the others

#### Scenario: A directory whose name starts with a period

- **WHEN** the checker is given ".notes", a directory that holds "scheduling.md"
- **THEN** it checks the requirements in "scheduling.md"

### Requirement: Directory with nothing to check message

If a directory that the EARS checker is given holds no file to check, then the EARS checker SHALL write a message naming the directory, and saying what files it looked for, to standard error.

#### Scenario: An empty directory

- **WHEN** the checker is given "notes", an empty directory
- **THEN** it writes `Cannot check notes: it holds no .md or .txt files` to standard error

#### Scenario: An openspec directory with no spec

- **WHEN** the checker is given "openspec", which holds only "config.yaml" and "changes/add-reminders/proposal.md"
- **THEN** it writes `Cannot check openspec: it holds no OpenSpec spec files` to standard error

#### Scenario: A walk through an openspec directory

- **WHEN** the checker is given "project", which holds only "package.json" and an "openspec" directory whose only Markdown is "changes/add-reminders/proposal.md"
- **THEN** it writes `Cannot check project: it holds no .md or .txt files outside openspec, and no OpenSpec spec files` to standard error
