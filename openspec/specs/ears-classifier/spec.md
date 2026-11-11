# ears-classifier Specification

## Purpose

Classifies a single natural-language requirement against the six EARS templates, producing a syntax tree for a conforming sentence or precise, fixable diagnostics for a nonconforming one.

## Requirements

### Requirement: Ubiquitous template

When the input matches the ubiquitous template, the EARS classifier SHALL return a ubiquitous syntax tree containing the system name and the response.

#### Scenario: Ubiquitous requirement

- **WHEN** the classifier receives "The checkout service SHALL display the order total." under the OpenSpec profile
- **THEN** it returns a ubiquitous tree with system "checkout service" and response "display the order total"

### Requirement: Event-driven template

When the input matches the event-driven template, the EARS classifier SHALL return an event-driven syntax tree containing the trigger, the system name, and the response.

#### Scenario: Event-driven requirement

- **WHEN** the classifier receives "When a customer submits an order, the checkout service SHALL send a confirmation email."
- **THEN** it returns an event-driven tree with trigger "a customer submits an order", system "checkout service", and response "send a confirmation email"

### Requirement: State-driven template

When the input matches the state-driven template, the EARS classifier SHALL return a state-driven syntax tree containing the precondition, the system name, and the response.

#### Scenario: State-driven requirement

- **WHEN** the classifier receives "While the cart is empty, the checkout service SHALL disable the pay button."
- **THEN** it returns a state-driven tree with precondition "the cart is empty", system "checkout service", and response "disable the pay button"

### Requirement: Unwanted-behavior template

When the input matches the unwanted-behavior template, the EARS classifier SHALL return an unwanted-behavior syntax tree containing the trigger, the system name, and the response.

#### Scenario: Unwanted-behavior requirement

- **WHEN** the classifier receives "If the inventory service is unreachable, then the checkout service SHALL show a stock warning."
- **THEN** it returns an unwanted-behavior tree with trigger "the inventory service is unreachable", system "checkout service", and response "show a stock warning"

### Requirement: Optional-feature template

When the input matches the optional-feature template, the EARS classifier SHALL return an optional-feature syntax tree containing the feature, the system name, and the response.

#### Scenario: Optional-feature requirement

- **WHEN** the classifier receives "Where gift wrapping is enabled, the checkout service SHALL offer a gift message field."
- **THEN** it returns an optional-feature tree with feature "gift wrapping is enabled", system "checkout service", and response "offer a gift message field"

### Requirement: Complex template

When the input has two or more leading clauses in the order Where, While, then When or If, the EARS classifier SHALL return a complex syntax tree containing each leading clause, the system name, and the response.

#### Scenario: State and event clauses

- **WHEN** the classifier receives "While a user is signed in, when the session expires, the access service SHALL redirect the user to the sign-in page."
- **THEN** it returns a complex tree with precondition "a user is signed in", trigger "the session expires", system "access service", and response "redirect the user to the sign-in page"

#### Scenario: Feature, state, and unwanted-behavior clauses

- **WHEN** the classifier receives "Where two-factor sign-in is enabled, while a user is signed in, if the device changes, then the access service SHALL request a second factor."
- **THEN** it returns a complex tree with feature "two-factor sign-in is enabled", precondition "a user is signed in", unwanted-behavior trigger "the device changes", system "access service", and response "request a second factor"

### Requirement: OpenSpec profile

Where the OpenSpec profile is selected, the EARS classifier SHALL accept the modal keyword only in uppercase.

#### Scenario: Uppercase keyword under the OpenSpec profile

- **WHEN** the classifier receives "The scheduler SHALL list upcoming events." under the OpenSpec profile
- **THEN** it returns a ubiquitous tree and no diagnostics

### Requirement: Classic profile

Where the classic profile is selected, the EARS classifier SHALL accept the modal keyword only in lowercase.

#### Scenario: Lowercase keyword under the classic profile

- **WHEN** the classifier receives "The scheduler shall list upcoming events." under the classic profile
- **THEN** it returns a ubiquitous tree and no diagnostics

### Requirement: Source spans

The EARS classifier SHALL attach to every syntax-tree element and every diagnostic a span of UTF-16 code-unit offsets into the original input.

#### Scenario: Span of a trigger

- **WHEN** the classifier receives "When a door opens, the access service SHALL log the event."
- **THEN** the trigger "a door opens" has the span from offset 5 to offset 17

### Requirement: Total classification

The EARS classifier SHALL return either a syntax tree or at least one diagnostic for every input string.

#### Scenario: Empty input

- **WHEN** the classifier receives an empty string
- **THEN** it returns at least one diagnostic and no syntax tree

### Requirement: Independent problems

When the input has several independent problems, the EARS classifier SHALL report one diagnostic for each problem.

#### Scenario: Wrong case and missing period

- **WHEN** the classifier receives "The scheduler shall list upcoming events" under the OpenSpec profile
- **THEN** it reports both a wrong-case diagnostic and a missing-period diagnostic

### Requirement: Canonical rendering

The EARS classifier SHALL render a syntax tree as canonical text that classifies under the same profile to an equal syntax tree, ignoring spans.

#### Scenario: Round trip of a complex requirement

- **WHEN** the tree from "While a user is signed in, when the session expires, the access service SHALL redirect the user to the sign-in page." is rendered and the result is classified again
- **THEN** the second tree equals the first, ignoring spans

### Requirement: Missing modal keyword

If the input contains neither the modal keyword in any letter case nor a weak modal verb after the system name, then the EARS classifier SHALL report a missing-modal diagnostic.

#### Scenario: No modal keyword

- **WHEN** the classifier receives "The checkout service displays the order total."
- **THEN** it reports a missing-modal diagnostic

### Requirement: Wrong modal case

If the modal keyword's letter case does not match the selected profile, then the EARS classifier SHALL report a wrong-case diagnostic whose fix replaces the keyword with the profile's form.

#### Scenario: Lowercase keyword under the OpenSpec profile

- **WHEN** the classifier receives "The scheduler shall list upcoming events." under the OpenSpec profile
- **THEN** it reports a wrong-case diagnostic whose fix produces "The scheduler SHALL list upcoming events."

### Requirement: Multiple responses

If the input contains more than one modal keyword, then the EARS classifier SHALL report a multiple-responses diagnostic.

#### Scenario: Two modal keywords

- **WHEN** the classifier receives "The checkout service SHALL save the cart and SHALL email a receipt."
- **THEN** it reports a multiple-responses diagnostic

### Requirement: Weak modal verb

If the input uses must, should, may, will, can, could, might, or would in any letter case where the modal keyword belongs, then the EARS classifier SHALL report a weak-modal diagnostic whose fix replaces that verb with the profile's keyword.

#### Scenario: Should in place of the keyword

- **WHEN** the classifier receives "The scheduler should list upcoming events." under the OpenSpec profile
- **THEN** it reports a weak-modal diagnostic whose fix produces "The scheduler SHALL list upcoming events."

### Requirement: Clause order

If the leading clauses do not appear in the order Where, While, then When or If, then the EARS classifier SHALL report a clause-order diagnostic whose fix reorders the clauses.

#### Scenario: Event clause before state clause

- **WHEN** the classifier receives "When the session expires, while a user is signed in, the access service SHALL redirect the user to the sign-in page."
- **THEN** it reports a clause-order diagnostic whose fix produces "While a user is signed in, when the session expires, the access service SHALL redirect the user to the sign-in page."

### Requirement: Repeated clause keyword

If a leading clause keyword appears more than once, then the EARS classifier SHALL report a repeated-clause diagnostic.

#### Scenario: Two When clauses

- **WHEN** the classifier receives "When a door opens, when the alarm is armed, the access service SHALL sound the alarm."
- **THEN** it reports a repeated-clause diagnostic

### Requirement: Conflicting triggers

If the input has both a When clause and an If clause, then the EARS classifier SHALL report a conflicting-triggers diagnostic.

#### Scenario: When and If together

- **WHEN** the classifier receives "When a door opens, if the alarm is armed, then the access service SHALL sound the alarm."
- **THEN** it reports a conflicting-triggers diagnostic

### Requirement: Missing then

If an If clause is not followed by the word then, then the EARS classifier SHALL report a missing-then diagnostic whose fix inserts it.

#### Scenario: If clause without then

- **WHEN** the classifier receives "If the inventory service is unreachable, the checkout service SHALL show a stock warning."
- **THEN** it reports a missing-then diagnostic whose fix produces "If the inventory service is unreachable, then the checkout service SHALL show a stock warning."

### Requirement: Missing comma

If a leading clause is not followed by a comma, then the EARS classifier SHALL report a missing-comma diagnostic whose fix inserts the comma.

#### Scenario: Event clause without a comma

- **WHEN** the classifier receives "When a customer submits an order the checkout service SHALL send a confirmation email."
- **THEN** it reports a missing-comma diagnostic whose fix produces "When a customer submits an order, the checkout service SHALL send a confirmation email."

### Requirement: Missing article

If the system name is not introduced by the article the, then the EARS classifier SHALL report a missing-article diagnostic whose fix inserts it.

#### Scenario: Bare system name

- **WHEN** the classifier receives "Scheduler SHALL list upcoming events."
- **THEN** it reports a missing-article diagnostic whose fix produces "The scheduler SHALL list upcoming events."

### Requirement: Empty response

If nothing but the final period follows the modal keyword, then the EARS classifier SHALL report an empty-response diagnostic.

#### Scenario: Keyword with no response

- **WHEN** the classifier receives "The scheduler SHALL."
- **THEN** it reports an empty-response diagnostic

### Requirement: Missing period

If the input does not end with a period, then the EARS classifier SHALL report a missing-period diagnostic whose fix appends one.

#### Scenario: No final period

- **WHEN** the classifier receives "The scheduler SHALL list upcoming events" under the OpenSpec profile
- **THEN** it reports a missing-period diagnostic whose fix produces "The scheduler SHALL list upcoming events."

### Requirement: Multiple sentences

If the input contains, outside inline code, a period followed by whitespace and further text, then the EARS classifier SHALL report a multiple-sentences diagnostic.

#### Scenario: Two sentences

- **WHEN** the classifier receives "The scheduler SHALL list upcoming events. It SHALL hide past events."
- **THEN** it reports a multiple-sentences diagnostic

### Requirement: Unrecognized structure

If the input matches no template and no more specific diagnostic applies, then the EARS classifier SHALL report an unrecognized-structure diagnostic.

#### Scenario: Keyword in an unrecognized position

- **WHEN** the classifier receives "SHALL the scheduler list upcoming events."
- **THEN** it reports an unrecognized-structure diagnostic

### Requirement: Scala.js linking without extra libraries

The EARS classifier SHALL link for Scala.js with no libraries beyond its declared dependencies.

#### Scenario: Application with no other dependencies

- **WHEN** a Scala.js application whose only dependency is the classifier's Scala.js artifact calls `Requirement.classify` and is linked for release
- **THEN** linking succeeds, with no reference to a class such as `java.util.Locale` that only another library provides

### Requirement: Diagnostic kind names

The EARS classifier SHALL give each diagnostic kind a distinct name in kebab case.

#### Scenario: Wrong case

- **WHEN** a caller asks for the name of the wrong-case diagnostic kind
- **THEN** the name is `wrong-case`

### Requirement: Inline code

The EARS classifier SHALL read a code span, from a run of backticks not escaped by a backslash to the next run of the same length, as one word whose content takes no part in classification.

#### Scenario: A quoted message with periods

- **WHEN** the classifier receives "The checker SHALL write `Cannot read notes.txt. Check the path.` to standard error." under the OpenSpec profile
- **THEN** it returns a ubiquitous syntax tree whose response contains the whole code span

#### Scenario: A quoted modal word

- **WHEN** the classifier receives "The scheduler SHALL reject an event titled `The calendar shall sync` with a warning." under the OpenSpec profile
- **THEN** it returns a ubiquitous syntax tree, with no multiple-responses diagnostic

#### Scenario: The only modal is quoted

- **WHEN** the classifier receives "The linter writes `SHALL` in uppercase." under the OpenSpec profile
- **THEN** it reports a missing-modal diagnostic

#### Scenario: Double backticks around a backtick

- **WHEN** the classifier receives "The formatter SHALL escape ``a ` b`` in output." under the OpenSpec profile
- **THEN** it returns a ubiquitous syntax tree whose response contains ``a ` b``

#### Scenario: An unmatched backtick

- **WHEN** the classifier receives "The shell SHALL reject a lone ` in a name. Then it SHALL stop." under the OpenSpec profile
- **THEN** it reports a multiple-sentences diagnostic, since the backtick quotes nothing

#### Scenario: Escaped backticks

- **WHEN** the classifier receives "The shell SHALL print \`now. Then\` on start." under the OpenSpec profile
- **THEN** it reports a multiple-sentences diagnostic, since escaped backticks quote nothing, as in Markdown

#### Scenario: An escaped backslash before code

- **WHEN** the classifier receives "The exporter SHALL write to C:\\`out. Then` daily." under the OpenSpec profile
- **THEN** it returns a ubiquitous syntax tree whose response contains the code span `` `out. Then` ``, since the backslash escapes the backslash before it, as in Markdown

#### Scenario: A system name in code

- **WHEN** the classifier receives "`job-runner` SHALL retry failed jobs." under the OpenSpec profile
- **THEN** it reports a missing-article diagnostic whose fix gives "The `job-runner` SHALL retry failed jobs."
