# Tasks

All work is on branch `add-npm-package`, in one pull request that also carries the archive. The
owner pushes from their own shell; Claude opens the pull request and reads runs with `gh`.
Compile, test, and format through Metals (BSP); use `./mill` only for `show`, the coverage
check, and the final CI-equivalent run. npm commands take `--cache` under a temporary directory.

## 1. Module, version, and facade (test-first)

- [x] 1.1 Add `object npm` inside `object ears` in `build.mill` (design Decision 1): `Base with ScalaJSModule`, the same Scala.js version as `ears.js`, `pureWarts`, `moduleDeps = Seq(ears.js)`, `moduleKind = ModuleKind.ESModule`, and not `Published`. Add `object test extends ScalaJSTests with ZioSpecs` with `moduleDeps` including `ears.js.test`. Run `import-build` and verify the new modules appear in `list-modules`, and that `./mill show __.publishArtifacts` lists no new artifact
- [x] 1.2 Change the snapshot version in `Published.publishVersion` to `0.2.0-SNAPSHOT` (design Decision 4, `release-publishing` delta). Verify with `./mill show ears.publishVersion`, with `EARS_RELEASE_VERSION` unset and empty, and that `0.1.0-rc1` still fails
- [x] 1.3 Add `FacadeSuite` (design Decision 7): one test per `npm-package` API scenario, the kind and template name table, and the property against `Requirement.classify` over `Generators.damagedSentence` and both profiles. Verify it fails to compile before 1.4
- [x] 1.4 Implement `Facade.classify` in `ears/npm/src/io/github/tsnee/ears/npm/Facade.scala` (design Decisions 2 and 3), including the comment on why it throws `TypeError`. Verify `FacadeSuite` passes on Node and every other suite still passes
- [x] 1.5 Check whether Mill's `ScoverageModule` can measure a `ScalaJSModule` (design, Risks). If it can, add a minimum for `ears.npm` and a `test-coverage` delta, then continue. If it can't, stop and ask the owner to accept the exception or take the shared-module alternative
  - 2026-11-04: it can't. Mixing `Covered` into the Scala.js module compiled its instrumented copy to JVM classes, which can't run the Node tests. The owner chose the shared-module alternative (design Decision 1, revised). `ears.npm` is now the measured JVM core (`Api`, `Value`, `ProfileArgument`; `ApiSuite`, 15 tests), at 100% statements and branches, with both minimums set to 100. `ears.npm.js` compiles the core plus `Facade` for Scala.js (`FacadeSuite` on Node, 10 tests). Added the `test-coverage` delta. Tasks 1.1, 1.3, and 1.4 hold for the revised layout: the Scala.js module is `ears.npm.js`, and the suites are `ApiSuite` and `FacadeSuite`
- [x] 1.6 Add `ears.npm` to the coverage report and coverage check in `.github/workflows/ci.yml` (CLAUDE.md's coverage commands change in 4.2). Verify with `yq`
- [x] 1.7 Make the library link on Scala.js without scala-java-locales (design Decision 10, `ears-classifier` delta): add `LetterCase` in `ears/src-jvm` and `ears/js/src`, use it for every `Locale.ROOT` call in `ears/src`, and split `ears`'s sources into `sharedSources` and `jvmSources`. Verify `./mill show ears.npm.js.fullLinkJS` links, that every `ears`, `ears.js`, and `ears.cli` test still passes, and that `ears/src` no longer mentions `java.util.Locale`

## 2. Package

- [x] 2.1 Write `ears/npm/package/index.d.ts` (design Decisions 2 and 6), with every field and array `readonly` and every type exported by name, and `ears/npm/package/README.md`, which covers install, a TypeScript example, the result shape, options, the `TypeError`s, and that the package isn't on npm yet
- [x] 2.2 Turn on Scala.js's minifier for `ears.npm`'s `fullLinkJS` (design Decision 5), finding how Mill 1.3.1 exposes it. Record `main.js`'s size with and without it
  - 2026-11-04: Mill 1.3.1 already sets `withMinify(true)` for `fullLinkJS` (checked in its compiled code), but Scala.js minifies release output only through Closure, which it doesn't use for ES modules; a `scala-cli` test agreed (14 kB as a script, 64 kB unminified as an ES module). `main.js` is 1.57 MB (243 kB gzipped), with or without the setting. The owner chose to ship it unminified (design Decision 5, revised); 4.2 adds a roadmap item to revisit it
- [x] 2.3 Add `ears.npm.npmPackage` (design Decision 5), including the source map with `sourcesContent` for every source it names. If dependency sources turn out impractical to embed, stop and ask the owner (design Decision 5). Verify with `./mill show ears.npm.npmPackage` and `jq`:
  - `package.json`'s name, version `0.2.0-dev`, license, `type`, `exports`, `types`, `sideEffects`, `engines`, `repository`, and no `dependencies` or `peerDependencies`;
  - with `EARS_RELEASE_VERSION=0.2.0`, version `0.2.0`;
  - the directory holds `main.js`, `main.js.map`, `index.d.ts`, `README.md`, and `LICENSE`;
  - every source from this repository that the map names has non-null `sourcesContent` (revised, see the note).

  Record the package's packed size
  - 2026-11-04: 240 of the map's 246 sources are library sources named by GitHub URL; the owner chose to embed only this repository's sources and keep library URLs (`npm-package` delta revised: "Embedded project sources", "Library sources by URL"). The link also showed `Api.scala` and `Facade.scala` named by absolute `file:///Users/…` paths: `ears.js`'s source-URI option moved into a `SourceUris` trait that `ears.npm.js` shares, and a "No build machine paths" requirement guards it. Mill lets a task read only declared sources, so the embedding reads through `ears.js`'s and `ears.npm.js`'s source roots. Verified: every field above; `0.2.0` with `EARS_RELEASE_VERSION=0.2.0`, with the six repository sources embedded under their `v0.2.0` GitHub URLs; the six repository sources embedded and 240 library sources without content; no `/Users/` path in any file. Packed: 584 kB (3.05 MB unpacked: `main.js` 1.57 MB, `main.js.map` 1.47 MB)

## 3. Smoke check

- [x] 3.1 Write `ears/npm/smoke/consumer.ts` (design Decision 8): `import type` the named types, narrow on `ok`, check the event-driven match, the wrong-case `fixed` sentence, and the unknown-profile `TypeError`, include the `@ts-expect-error` assignment to `ok`, and throw on any mismatch
- [x] 3.2 Write `scripts/check-npm-package` (design Decision 8): build, `npm pack`, publint, attw with `--profile esm-only`, install into an empty ES-module project, the `npm ls --json` and `package.json` dependency checks with `jq`, `tsc` under Node and bundler resolution with the flags in Decision 8, and `node consumer.ts`. Verify it passes locally, and fails in each of these temporary breakages, each reverted afterwards:
  - `template` removed from the declared match result;
  - `readonly` removed from `ok`;
  - `classify` renamed in `consumer.ts`;
  - a wrong expected `fixed` sentence;
  - `index.d.ts` left out of the package;
  - a type imported without `import type` in `consumer.ts`
  - 2026-11-05: passes locally (Node 25.2.1, TypeScript 7.0.2, publint 0.3.25, attw 0.18.5): publint "All good", attw green for Node ESM and bundler resolution (CommonJS ignored by `--profile esm-only`). Five breakages failed the check as intended: the missing `template` and the renamed `classify` with TS2339 and TS2552, the wrong `fixed` sentence at run time, `index.d.ts` left out of `files` in publint, and the type import in TS1484. Removing `readonly` from `ok` passed at first: the `@ts-expect-error` line assigned `false` to `ok`, a type error with or without `readonly`. Fixed in `consumer.ts` by assigning a well-typed `template`; removing `readonly` from `template` now fails with TS2578. Every file was restored after its run
- [x] 3.3 In `.github/workflows/ci.yml` (design Decision 8): add `typescript@7.0.2`, `publint@0.3.25`, and `@arethetypeswrong/cli@0.18.5` to both jobs' pinned `npm install -g`; add a "Check the npm package" step to `check` after "Test"; in `check-jdk17`, change Node to 22, add the same step, and update the job's comment. Verify with `yq` that the workflow parses and each job's steps are in the right order

## 4. Documentation

- [x] 4.1 Update `README.md`: a JavaScript and TypeScript section (building the package locally and a short example), and the snapshot version `0.2.0-SNAPSHOT` for `publishLocal`. Verify with `mq`
- [x] 4.2 Keep `CLAUDE.md` to lasting rules and facts:
  - Add the lasting facts: Build and test (the `npmPackage` task, `scripts/check-npm-package`, and `ears.npm` in the coverage commands), the CI checks and the oldest-runtimes job, and Layout (`ears/npm/`, the `npm-package` spec, `docs/roadmap.md`).
  - Make Releasing say that builds outside releases use the next version as a snapshot (`-SNAPSHOT` on Maven, `-dev` on npm), bumped after each release, without naming the number.
  - Move "Likely next steps" to a new `docs/roadmap.md`, with npm publishing next, and an item to revisit minifying the npm package (design Decision 5).
  - Remove the paragraph on the repository's extraction and its first change, which is history already in `docs/worklog.md`.
  - Trim the Build and test line of exact versions (Mill, Scala, JDK), which `build.mill` records; keep only that the header pins the JVM index and why.

  Verify both files with `mq`

## 5. Checks, review, and pull request

- [x] 5.1 Run `./mill __.checkFormat`, `./mill __.test`, both modules' `validateCoverageMinimums`, `scripts/check-npm-package`, and `scripts/check-openspec`. Verify all pass, and report the coverage and the package size. Add a dated line to `docs/worklog.md`
- [x] 5.2 Run `/code-review` in a subagent on the branch's diff, and let the owner choose which findings to fix
  - 2026-11-05: the review (done by hand in the subagent, since the skill doesn't clearly cover untracked files) found 8 issues; the owner chose to fix all. (1) Options with no prototype were refused: `instanceof Object` became a `typeof` test, with a Node test. (2) A `file:` URI in the map was kept unembedded: the embedding now fails on anything but a library `https://` URL or one of the package's sources (checked by dropping `SourceUris` from `ears.npm.js`: the build fails naming the `file:` URI), and `check-npm-package` fails if any packed file contains the repository path or `$HOME`. (3) The dependency check read only `npm ls`'s top level: it now counts every installed package with `--parseable`, and checks optional and bundled dependencies too. (4) A third argument threw `No matching overload`: one export replaces the two overloads, and `consumer.ts` calls it with one and three arguments. (5) Read-only was checked only on `template`: `consumer.ts` now checks `ok`, `diagnostics`, and `edits` (removing `readonly` from `ok` or `diagnostics` fails with TS2578). (6) The script now cleans up on INT and TERM. (7) The kind names moved into the library as `DiagnosticKind.kebabName`, used by the CLI and `Api` (`ears-classifier` delta: "Diagnostic kind names"; `ApiSuite`'s kind-name test moved to `DiagnosticSuite`). (8) An unrecognized source URI now fails with that message instead of blaming the repository. Spec scenarios added for options with no prototype, null options, extra arguments, and read-only arrays. All checks pass: 281 tests; coverage `ears` 95.73% / 92.04%, `ears.cli` 97.06% / 97.06%, `ears.npm` 100% / 100%
- [x] 5.3 Commit; the owner pushes. Open the pull request with `gh`, recommending squash merge. Report the CI run's result and minutes, for both jobs. After CI passes, archive the change on the same branch (syncing `npm-package`, `continuous-integration`, `release-publishing`, `test-coverage`, and `ears-classifier`) for the owner to push and merge
  - 2026-11-05: committed as b96a5f3, pushed by the owner; PR #13. CI run 41977076242 passed: `check` 2m 37s, `check-jdk17` 2m 04s (the npm package check on Node 26 and 22). Archived on the same branch
After the merge (not a task here, since it can't happen before the archive; `docs/roadmap.md` lists it): the owner tags `v0.1.1` on `main` for the Scala.js linking fix (design Decision 10). Report the release run, and confirm on the Central Portal that the deployment holds version `0.1.1`. Once it's published, a small follow-up moves the README's install lines and its "0.1.0 is on Maven Central" line to 0.1.1 (Scala.js users need it), folded into the next change's pull request
