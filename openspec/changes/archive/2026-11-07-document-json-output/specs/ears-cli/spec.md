## MODIFIED Requirements

### Requirement: JSON output
Where JSON output is selected, the EARS checker SHALL write all findings as one JSON document to standard output.

#### Scenario: No findings as JSON
- **WHEN** the checker finds no problems with JSON output selected
- **THEN** it writes one JSON document with an empty list of findings

#### Scenario: Several inputs as JSON
- **WHEN** the checker is given "a.txt" and "b.txt" with JSON output selected, and each has one requirement
- **THEN** it writes one JSON document whose `checked` is 2, followed by one line break

## ADDED Requirements

### Requirement: JSON format version
Where JSON output is selected, the EARS checker SHALL write the format version 1 as the document's `version` field.

#### Scenario: Version of a document
- **WHEN** the checker reads plain text "The scheduler shall list upcoming events." with JSON output selected
- **THEN** the document starts with `{"version":1,`

### Requirement: JSON checker release
Where JSON output is selected, the EARS checker SHALL write the version of the release that wrote the document as its `checker` field.

#### Scenario: A release build
- **WHEN** the checker built for release 0.2.0 reads plain text "The scheduler shall list upcoming events." with JSON output selected
- **THEN** it writes `{"version":1,"checker":"0.2.0","checked":1,"findings":[]}`

#### Scenario: A snapshot build
- **WHEN** a checker built between releases, after 0.1.1, writes JSON output
- **THEN** its `checker` field is the next version as a snapshot, such as "0.2.0-SNAPSHOT"

### Requirement: JSON findings
Where JSON output is selected, the EARS checker SHALL write each finding as an object with its location, the requirement's text, the diagnostic's kind, its message, its span, and its fix.

#### Scenario: A finding on a line
- **WHEN** the checker reads the file "reqs.txt" whose line 2 is "The scheduler should list upcoming events." with JSON output selected
- **THEN** the finding has the location `{"type":"line","file":"reqs.txt","line":2}`, the requirement "The scheduler should list upcoming events.", the kind "weak-modal", the diagnostic's message, a span with `start` and `end`, and the fix "The scheduler shall list upcoming events."

### Requirement: JSON spans
Where JSON output is selected, the EARS checker SHALL give each finding's span as the half-open range of UTF-16 code units in the finding's requirement text that the diagnostic is about.

#### Scenario: Span of a weak modal
- **WHEN** the checker reads plain text "The scheduler should list upcoming events." with JSON output selected
- **THEN** the finding's span is `{"start":14,"end":20}`, the position of "should" in the requirement

#### Scenario: Span after a character outside the Basic Multilingual Plane
- **WHEN** the checker reads plain text "The 📅 scheduler should list upcoming events." with JSON output selected
- **THEN** the finding's span counts the calendar emoji as two code units, so its `start` is 17

### Requirement: JSON line locations
Where JSON output is selected, when a finding is in plain text, Markdown, or standard input, the EARS checker SHALL give its location as an object with the type "line", the file name, and the 1-based line the requirement starts on.

#### Scenario: Standard input location
- **WHEN** the checker reads "The scheduler lists upcoming events." from standard input with JSON output selected
- **THEN** the finding's location is `{"type":"line","file":"<stdin>","line":1}`

### Requirement: JSON change locations
Where JSON output is selected, when a finding is in a requirement of an OpenSpec change, the EARS checker SHALL give its location as an object with the type "change" and the change's, capability's, and requirement's names.

#### Scenario: Change requirement location
- **WHEN** the checker reads change JSON for change "add-reminders" whose requirement "Reminder sent" in capability "reminders" lacks the modal keyword, with JSON output selected
- **THEN** the finding's location is `{"type":"change","change":"add-reminders","capability":"reminders","requirement":"Reminder sent"}`

### Requirement: JSON spec locations
Where JSON output is selected, when a finding is in a requirement of an OpenSpec spec, the EARS checker SHALL give its location as an object with the type "spec" and the spec's and requirement's names.

#### Scenario: Spec requirement location
- **WHEN** the checker reads spec JSON for spec "reminders" whose requirement "Upcoming" lacks the modal keyword, with JSON output selected
- **THEN** the finding's location is `{"type":"spec","spec":"reminders","requirement":"Upcoming"}`

### Requirement: JSON finding without a fix
Where JSON output is selected, when a diagnostic has no fix, the EARS checker SHALL write the finding's fix as null.

#### Scenario: Missing modal keyword
- **WHEN** the checker reads plain text "The scheduler lists upcoming events." with JSON output selected
- **THEN** the finding's kind is "missing-modal" and its fix is `null`
