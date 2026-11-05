## ADDED Requirements

### Requirement: npm package failures
If the npm package fails to build, to install into an empty project, or to classify a sentence on Node, then the CI workflow SHALL fail the run.

#### Scenario: Broken export
- **WHEN** a pushed commit renames the exported `classify` function
- **THEN** the run fails at the npm package check

### Requirement: TypeScript declaration failures
If a TypeScript consumer of the installed npm package fails to type-check against the package's declarations under Node or bundler module resolution, then the CI workflow SHALL fail the run.

#### Scenario: Declaration out of step
- **WHEN** a pushed commit removes `template` from the declared result of a matching sentence
- **THEN** the run fails at the npm package check

### Requirement: Package lint failures
If publint or Are the Types Wrong reports a problem with the packed npm package, then the CI workflow SHALL fail the run.

#### Scenario: Missing declaration file
- **WHEN** a pushed commit stops copying `index.d.ts` into the package
- **THEN** the run fails at the npm package check

### Requirement: npm package check on the oldest Node
The CI workflow SHALL run the npm package check on the oldest Node version the package declares, as well as on the Node version the build uses.

#### Scenario: Node 22
- **WHEN** a CI run checks a pull request
- **THEN** the npm package check runs once on Node 22 and once on Node 26
