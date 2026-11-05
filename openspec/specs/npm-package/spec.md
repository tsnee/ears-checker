# npm-package Specification

## Purpose
Makes the EARS classifier usable from JavaScript and TypeScript as the npm package
`@tsnee/ears-checker`, with the same results as the Scala library.

## Requirements

### Requirement: Package name
The npm package SHALL be named `@tsnee/ears-checker`.

#### Scenario: Name in the package metadata
- **WHEN** a developer builds the npm package and reads its `package.json`
- **THEN** its `name` is `@tsnee/ears-checker`

### Requirement: ES module export
The npm package SHALL export a function named `classify` from an ES module.

#### Scenario: Import from Node
- **WHEN** a project that depends on the package runs `import { classify } from "@tsnee/ears-checker"` on Node
- **THEN** `classify` is a function

### Requirement: Matching sentence
When `classify` is given a sentence that matches an EARS template, the npm package SHALL return an object with `ok` set to `true`, the template's kebab-case name, and the requirement's clauses.

#### Scenario: Event-driven requirement
- **WHEN** a caller classifies `When a user submits an order, the checkout service shall send a confirmation email.` with the classic profile
- **THEN** the result has `ok: true` and `template: "event-driven"`
- **AND** its requirement has an `event` trigger with the text `a user submits an order`, the system `checkout service`, and the response `send a confirmation email`, and has `null` for the feature and the precondition

### Requirement: Non-matching sentence
When `classify` is given a sentence that doesn't match an EARS template, the npm package SHALL return an object with `ok` set to `false` and every diagnostic the Scala library reports for that sentence, in the library's order.

#### Scenario: Missing modal
- **WHEN** a caller classifies `The scheduler lists upcoming events.` with the classic profile
- **THEN** the result has `ok: false` and a diagnostic with `kind: "missing-modal"` and a message

#### Scenario: Same diagnostics as the library
- **WHEN** a caller classifies any sentence with either profile
- **THEN** the result's diagnostics have the same kinds, messages, spans, and fixes, in the same order, as the Scala library's for that sentence and profile

### Requirement: Diagnostic kind names
The npm package SHALL name each diagnostic kind in kebab case, with the same name the command line's JSON output uses.

#### Scenario: Wrong case
- **WHEN** a caller classifies `The scheduler shall list upcoming events.` with the OpenSpec profile
- **THEN** the result's diagnostic has `kind: "wrong-case"`

### Requirement: Fixed sentence
Where a diagnostic has a fix, the npm package SHALL give the fix's edits and the sentence with those edits applied.

#### Scenario: Fix for the modal's case
- **WHEN** a caller classifies `The scheduler shall list upcoming events.` with the OpenSpec profile
- **THEN** the diagnostic's `fix.fixed` is `The scheduler SHALL list upcoming events.`

#### Scenario: Diagnostic without a fix
- **WHEN** a caller classifies `Upcoming events.` with the classic profile
- **THEN** every diagnostic in the result that has no fix in the Scala library has `fix: null`

### Requirement: Span offsets
The npm package SHALL give each span as `start` and `end` offsets in UTF-16 code units, so that `sentence.slice(start, end)` is the span's text.

#### Scenario: Clause span
- **WHEN** a caller classifies `The scheduler shall list upcoming events.` with the classic profile
- **THEN** slicing the sentence at the system clause's span gives `scheduler`

### Requirement: Profile option
When `classify` is given an options object with a `profile` of `"classic"` or `"openspec"`, the npm package SHALL classify the sentence with that profile.

#### Scenario: OpenSpec profile
- **WHEN** a caller classifies `The scheduler SHALL list upcoming events.` with `{ profile: "openspec" }`
- **THEN** the result has `ok: true`

#### Scenario: Options with no prototype
- **WHEN** a caller classifies `The scheduler SHALL list upcoming events.` with an object made by `Object.create(null)` whose `profile` is `"openspec"`
- **THEN** the result has `ok: true`

### Requirement: Extra arguments
When `classify` is given more than two arguments, the npm package SHALL ignore every argument after the second.

#### Scenario: Third argument
- **WHEN** a plain JavaScript caller classifies `The scheduler SHALL list upcoming events.` with `{ profile: "openspec" }` and `42`
- **THEN** the result has `ok: true`

### Requirement: Default profile
When `classify` is called without options, or with options that have no `profile`, the npm package SHALL classify the sentence with the classic profile.

#### Scenario: No options
- **WHEN** a caller classifies `The scheduler shall list upcoming events.` with no second argument
- **THEN** the result has `ok: true`

#### Scenario: Empty options
- **WHEN** a caller classifies `The scheduler shall list upcoming events.` with `{}`
- **THEN** the result has `ok: true`

### Requirement: Unknown profile
If `classify` is given a profile other than `"classic"` or `"openspec"`, then the npm package SHALL throw a `TypeError` that names the accepted profiles.

#### Scenario: Misspelled profile
- **WHEN** a caller classifies a sentence with `{ profile: "open-spec" }`
- **THEN** `classify` throws a `TypeError` whose message mentions `classic` and `openspec`

### Requirement: Options that aren't an object
If `classify` is given a second argument that is neither `undefined` nor an object, then the npm package SHALL throw a `TypeError`.

#### Scenario: Profile passed as a string
- **WHEN** a plain JavaScript caller passes `"openspec"` as the second argument
- **THEN** `classify` throws a `TypeError`

#### Scenario: Null options
- **WHEN** a plain JavaScript caller passes `null` as the second argument
- **THEN** `classify` throws a `TypeError`

### Requirement: Sentence that isn't a string
If `classify` is given a sentence that isn't a string, then the npm package SHALL throw a `TypeError`.

#### Scenario: Number instead of text
- **WHEN** a plain JavaScript caller passes `42` as the sentence
- **THEN** `classify` throws a `TypeError`

### Requirement: TypeScript declarations
The npm package SHALL include TypeScript declarations for every export.

#### Scenario: Narrowing a result
- **WHEN** a TypeScript project with `strict` enabled checks a result's `ok` field and then reads `template` in the `true` branch and `diagnostics` in the `false` branch
- **THEN** the project type-checks with no errors

### Requirement: Named result types
The npm package SHALL export a named TypeScript type for the options, the result, and each shape the result contains.

#### Scenario: Annotating a diagnostic
- **WHEN** a TypeScript project imports the types `Result`, `Diagnostic`, `DiagnosticKind`, `Template`, `Clause`, `Span`, `Options`, and `Profile` from the package
- **THEN** the project type-checks with no errors

### Requirement: Read-only results
The npm package SHALL declare every field and array of a result as read-only.

#### Scenario: Assigning to a result
- **WHEN** a TypeScript project assigns `true` to the `ok` field of a result it has narrowed to a match
- **THEN** the project fails to type-check

#### Scenario: Changing a result's array
- **WHEN** a TypeScript project calls `pop()` on a result's `diagnostics`, or on a fix's `edits`
- **THEN** the project fails to type-check

### Requirement: Release version
While a release version is set, the npm package SHALL carry that version.

#### Scenario: Release build
- **WHEN** a developer builds the npm package with the release version `0.2.0`
- **THEN** its `package.json` has `version: "0.2.0"`

### Requirement: Development version
While no release version is set, the npm package SHALL carry version `0.2.0-dev`.

#### Scenario: Development build
- **WHEN** a developer builds the npm package with no release version set
- **THEN** its `package.json` has `version: "0.2.0-dev"`

### Requirement: Package license
The npm package SHALL declare the license `CC0-1.0` and include the license text.

#### Scenario: License in the package
- **WHEN** a developer builds the npm package
- **THEN** its `package.json` has `license: "CC0-1.0"` and the package contains a `LICENSE` file

### Requirement: Repository URL
The npm package SHALL name `git+https://github.com/tsnee/ears-checker.git` as its repository URL.

#### Scenario: Repository in the package metadata
- **WHEN** a developer builds the npm package and reads its `package.json`
- **THEN** `repository` is `{"type": "git", "url": "git+https://github.com/tsnee/ears-checker.git"}`

### Requirement: Supported Node versions
The npm package SHALL declare Node 22 as the oldest Node version it supports.

#### Scenario: Engines in the package metadata
- **WHEN** a developer builds the npm package and reads its `package.json`
- **THEN** `engines.node` is `>=22`

### Requirement: No side effects on import
The npm package SHALL declare that importing it has no side effects.

#### Scenario: Side effects in the package metadata
- **WHEN** a developer builds the npm package and reads its `package.json`
- **THEN** `sideEffects` is `false`

### Requirement: Embedded project sources
The npm package SHALL ship a source map that embeds the content of every source from this repository that it names.

#### Scenario: Debugging into the classifier
- **WHEN** a developer's debugger reads the package's source map
- **THEN** `Classifier.scala` and every other source from this repository that the map names has its content in the map

### Requirement: Library sources by URL
The npm package's source map SHALL name each source from a library dependency by its public URL at that library's released version.

#### Scenario: Scala standard library source
- **WHEN** a developer reads the package's source map
- **THEN** a Scala 3 standard library source is named by a `https://raw.githubusercontent.com/scala/scala3/3.10.0/…` URL

### Requirement: No build machine paths
The npm package SHALL contain no file path from the machine that built it.

#### Scenario: Built on a developer machine
- **WHEN** a developer whose repository is under `/Users/alex/src` builds the npm package
- **THEN** no file in the package contains `/Users/alex`

### Requirement: No runtime dependencies
The npm package SHALL declare no runtime dependencies.

#### Scenario: Installing the package
- **WHEN** a project installs the packed npm package into an empty `node_modules`
- **THEN** only `@tsnee/ears-checker` is installed
