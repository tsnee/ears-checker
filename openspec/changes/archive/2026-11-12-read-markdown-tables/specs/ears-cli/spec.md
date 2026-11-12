## ADDED Requirements

### Requirement: Warnings in text output

Where text output is selected, the EARS checker SHALL write one line per warning to standard output, with the warning's location, the word `warning`, its kind, and its message.

#### Scenario: A table row with extra cells

- **WHEN** the checker reads `notes.md`, whose two-column table has the body row `| C | one | two | three |` on line 9, with text output
- **THEN** standard output has the line `notes.md:9: warning: table-extra-cells: This table row has 4 cells and its header 2, so GitHub doesn't show the last 2.`

### Requirement: Warnings in line order

Where text output is selected, the EARS checker SHALL write an input's findings and warnings in the order of their lines.

#### Scenario: A warning between two findings

- **WHEN** an input has findings on lines 3 and 12 and a warning on line 9
- **THEN** the warning's line comes between the two findings' lines

### Requirement: Warnings in JSON output

Where JSON output is selected, the EARS checker SHALL write each warning as an object with its location, kind, and message in the document's `warnings` array.

#### Scenario: A JSON warning

- **WHEN** the checker reads the same `notes.md` with JSON output
- **THEN** the document's `warnings` array holds `{"location":{"type":"line","file":"notes.md","line":9},"kind":"table-extra-cells","message":"This table row has 4 cells and its header 2, so GitHub doesn't show the last 2."}`

#### Scenario: No warnings

- **WHEN** the checker reads input with no warnings, with JSON output
- **THEN** the document's `warnings` field is an empty array

### Requirement: Exit status without warnings

The EARS checker SHALL set the exit status from the findings, unaffected by warnings.

#### Scenario: Only a warning

- **WHEN** the checker reads a Markdown file whose only problem is a table row with extra cells
- **THEN** it writes the warning and exits with status 0

### Requirement: Warnings logged

When the EARS checker reports a warning, the EARS checker SHALL log a warning entry with the input's name, the warning's line and kind, and its message.

#### Scenario: Logged warning

- **WHEN** the checker reports the warning for line 9 of `notes.md`
- **THEN** its log has a WARN entry naming `notes.md`, line 9, and `table-extra-cells`
