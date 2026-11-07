## ADDED Requirements

### Requirement: Library code size
The npm package SHALL ship its code in a `main.js` of at most 700,000 bytes.

#### Scenario: Development build
- **WHEN** a developer builds the npm package
- **THEN** its `main.js` is at most 700,000 bytes

### Requirement: Source map of the shipped code
The npm package's source map SHALL map the shipped code to the Scala sources it was compiled from.

#### Scenario: Stepping into classify
- **WHEN** a developer's debugger looks up the start of the function that `main.js` exports as `classify`
- **THEN** the source map gives a line in `Facade.scala`
