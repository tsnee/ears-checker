# Proposal

## Why

An adversarial review of every test suite on 2026-11-11, in three parts (the classifier library and npm package; the CLI and `scripts/cli-cases`; the Markdown facade, the link-check generator, and the release-notes tool), broke the production code each suspect test claims to cover and reran the suite. No suite is hollow, but some tests can't fail for the reason their names give, some property tests reach their case only 1 to 2% of the time (two span breaks in the npm API went uncaught), some tests duplicate others, and two comparison cases can't tell the jar, Node, and native builds apart. Coverage counted all of them, so the gaps were invisible. The owner approved every fix the review proposed.

## What Changes

- **Tests that can't fail for the reason their names give are fixed**, each first shown to fail against the break the review found:
  - release-notes `MainSuite`'s standard error is cleared between runs (`clearOutput` clears standard output only);
  - `SourceLinesSuite`'s escape tests use a case where a missed escape changes the line;
  - `HelpersSuite`'s first-candidate test can see which success wins and what was tried;
  - `LinkCheckSuite` covers a private owner further up;
  - the bootstrap logger tests: the shared one is removed, and the JVM's checks the UTF-8 bytes it writes, through a new `Main` seam that takes the output stream;
  - `MainSuite`'s error-with-no-message test checks the logged value;
  - `SettingsSuite` compares with a literal, not with the code itself.
- **Weak tests are strengthened**: a shared valid-requirement generator for the npm API's property and the span law, a Where + While template case, the parse-error message, a MODIFIED delta, the logged cause, two lines of standard input, a line-separator-rich character mix for the log property, the weak-modal test, a speed-guard name, and the link-check summary derived from the program.
- **True duplicates are removed**, and four shared-behavior tests move from the JVM-only `MainSuite` into `CliSuite`, so they also run on Node.
- **`scripts/cli-cases`**: the OpenSpec text case uses fixture JSON with findings, in both output formats; the byte-order-mark case compares JSON; the OpenSpec cases expect exit status 0; the verbose case requires a debug line.
- No behavior changes: the one production change is the `Main` seam, which the program calls with standard error as before.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. No requirement changes; the change only makes the tests check what the specs already require (`skip_specs: true`).

## Impact

- Test sources in `ears/test`, `ears/npm/test`, `ears/cli/test` (shared and JVM), `ears/markdown/test`, `ears/linkCheck/test`, and `ears/releaseNotes/test`; `ears/test`'s `Generators`.
- `ears/cli/src-jvm/.../Main.scala`: a `bootstrapTo` seam.
- `scripts/cli-cases` and fixture JSON beside it; the scripts that source it (`check-npm-cli`, `check-native-cli`, `check-coursier-app`) are unchanged.
- Coverage figures may move slightly as duplicates go; the minimums must still hold.
