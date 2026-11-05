# Tasks

All work is on branch `add-scalajs-link-check`, in one pull request that also carries the
archive. The owner pushes from their own shell; Claude opens the pull request and reads runs
with `gh`. Compile, test, and format through Metals (BSP) where it can; `publishLocal`, `show`,
`run`, the coverage check, and the final CI-equivalent run go through `./mill`.

## 1. The generator (test-first)

- [x] 1.1 Add `object linkCheck` inside `object ears` in `build.mill` (design Decision 1): `Base with Covered`, `semanticdb-shared` 4.17.4 and ZIO, not `Published`; and `object test extends CoveredTests with ZioSpecs` with `forkEnv` giving `ears.semanticDbData()`. Run `import-build` and verify the modules appear in `list-modules` and `./mill show __.publishArtifacts` lists nothing new
- [x] 1.2 Write the `LinkCheck` suites (design Decision 4): the rules on small `TextDocument` values, the program text, and the real library's invariants. Verify they fail to compile before 1.3
- [x] 1.3 Implement `LinkCheck.operations` and `LinkCheck.program` (design Decisions 2 and 3). Verify the suites pass, and check once that the real library gives the 7 operations in design Context, spot-checking its SemanticDB with `metap`
  - 2026-11-05: 17 tests pass through Mill. The real-library test reads `EARS_SEMANTICDB` through `Live`, as `OwnSpecsSuite` does: zio-test's `TestSystem` starts with no environment (Metals' runner also leaves `forkEnv` out, so that one test runs only through Mill). Generated from the real library, the program references exactly the 7 operations; `metap` shows each extension takes its receiver as its first parameter list, so `Owner.member` eta-expands to a function
- [x] 1.4 Write `Main` and its suite (design Decisions 1 and 4). Verify it passes, then measure coverage and set `ears.linkCheck`'s minimums near what it reaches
  - 2026-11-05: `MainSuite` (4 tests) and two added rule tests (a class with no type parameters; a public `val` of an object, a term rather than a method descriptor) bring `ears.linkCheck` to 96.84% statements and 100% branches. The rest is `Main.bootstrap` and `Main.run`, which only a real process runs. Minimums: 96 and 100. The owner's-information lookup in `reference` became a `collect` chain, since an operation's owners are already known to be public and the missing case can't happen

## 2. The link check

- [x] 2.1 Write `scripts/check-scalajs-link` (design Decision 5). Verify it passes and record its run time; that a second run after changing the library picks up the new jar (or add `--ttl 0`); and, each change reverted afterwards, that it fails with a link error naming `java.util.Locale` when `Rendering` calls `toUpperCase(java.util.Locale.ROOT)`, that a new public extension on `Requirement` is referenced in the generated program with no other edit, and that a second public `classify` overload fails the check naming `Requirement.classify`
  - 2026-11-05: passes locally in 7 to 10 s (the first run downloads scala-cli's Scala.js linker). A changed jar is picked up without `--ttl 0`, as the Locale breakage showed. Breakages, each reverted: `Rendering` calling `toUpperCase(java.util.Locale.ROOT)` fails to link (`Referring to non-existent method static java.util.Locale.ROOT()`), which the npm package check wouldn't catch, since `render` isn't reachable from `classify`. Replacing `upperCase` instead left the `LetterCase` import unused, which fails the build first, so the breakage keeps it and adds the `Locale` call. A new extension, `clauseCount`, was linked with no other edit (8 operations). A second `classify` fails the check: "Requirement.classify is overloaded"
- [x] 2.2 In `.github/workflows/ci.yml` (design Decision 6): add `VirtusLab/scala-cli-setup` (pinned by commit, `scala-cli-version: 1.18.0`) and a "Check the Scala.js library" step to `check` after "Check the npm package", and add `ears.linkCheck` to the coverage report loop and the coverage check. Verify with `yq` that the workflow parses and the steps are in order

## 3. Documentation

- [x] 3.1 Update `README.md` (design Decision 7): every `0.1.0` install line and the "on Maven Central" line to `0.1.1`, the Scala.js note, and the new script under Building. Verify with `mq`, and that no `0.1.0` remains outside history
- [x] 3.2 Update `docs/roadmap.md` and `CLAUDE.md` (design Decision 7). Verify both with `mq`

## 4. Checks, review, and pull request

- [x] 4.1 Run `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, `scripts/check-scalajs-link`, `scripts/check-npm-package`, and `scripts/check-openspec`. Verify all pass, and report the coverage. Add a dated line to `docs/worklog.md`
  - 2026-11-05: all pass; 304 tests. Coverage: `ears` 95.73% / 92.04%, `ears.cli` 97.06% / 97.06%, `ears.npm` 100% / 100%, `ears.linkCheck` 96.84% / 100% (minimums 96 / 100)
- [x] 4.2 Run `/code-review` in a subagent on the branch's diff, and let the owner choose which findings to fix
  - 2026-11-05: the review compiled each reference form and found 8 issues; the owner chose to fix all.
    1. Bare references didn't compile for `()` methods, varargs, value-typed `val`s and parameterless `def`s, or by-name parameters, whose type broke inference of the whole list. References are now ascribed one by one, by the signature's shape (`: AnyRef` for methods with parameters, `: Any` for values, `() => m()` for an empty first list), in a `List[Any]`; varargs is a named problem. A new fixture module, `ears.linkCheck.fixture`, has 20 operations of every referenced shape. The script compiles the program generated from it, and a test pins its references.
    2. Inline methods (SemanticDB kind `MACRO`) were skipped; now they're included.
    3. A class nested in a class is now written `Outer#Inner`.
    4. The script's last-line comparison is documented as showing only that the generated program ran.
    5. The generator reads `ears.js.semanticDbData` (shared and Scala.js-only sources) instead of the JVM build's.
    6. The script reads every value in one `./mill show '{…}'`, fails on a missing value or directory, and names `jq` among its needs.
    7. Reading and writing failures have their own messages.
    8. New tests for `private` and `protected` members; the real-library test's name filter now sees `val`s.

    313 tests pass; `ears.linkCheck` is at 97.28% / 100%; the `Locale.ROOT` breakage still fails to link
- [ ] 4.3 Commit; the owner pushes. Open the pull request with `gh`, recommending squash merge. Report the CI run's result and minutes. After CI passes, archive the change on the same branch (syncing `continuous-integration` and `test-coverage`) for the owner to push and merge
