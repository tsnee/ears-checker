# Proposal

## Why

The classifier already builds with Scala.js (`ears.js`) and its tests run on Node, but nothing calls it from JavaScript or TypeScript. Scala.js exports nothing by default, and the Scala.js jar on Maven Central is usable only from other Scala.js builds. An npm package would let editor plugins, web pages, and lint scripts check EARS requirements with the same classifier as the CLI.

## What Changes

- A small JavaScript-facing API over `ears.js`: a pure core, measured on the JVM like the rest, and a thin Scala.js export. It exports one function, `classify(sentence, options?)`:
  - **`sentence`:** a string holding one requirement, for example `"When a user submits an order, the checkout service shall send a confirmation email."`.
  - **`options`:** an optional object, so options can be added later without breaking callers. Today it has one field, `profile`:
    - `"classic"` expects a lowercase `shall`, and is the default;
    - `"openspec"` expects `SHALL`, as OpenSpec's validator does.
  - **The result:** a plain, read-only JavaScript object whose `ok` field says which of two shapes it has:
    - **A match (`ok: true`):** the template's name, such as `"event-driven"`, and the requirement's clauses (feature, precondition, trigger, system, and response), each with its text and its span (start and end offsets in the sentence). A clause the sentence doesn't have is `null`.
    - **No match (`ok: false`):** every diagnostic, each with its kind (such as `"missing-modal"`), message, and span. A diagnostic that can be fixed also has its edits and the sentence with them applied; one that can't has `fix: null`.
  - **Errors:** a `TypeError` when `sentence` isn't a string, `options` isn't an object, or `profile` isn't one of the two names. A sentence that doesn't fit EARS is never an error; it's an `ok: false` result.
- Kind names, spans, and absent values follow the CLI's JSON output: kebab-case kinds, `span: {start, end}`, and `null`. Only `fix` differs: the CLI gives just the corrected sentence, and the package gives the edits too.
- The module links as an ES module (Scala.js doesn't minify ES modules; it's 243 kB gzipped). A Mill task assembles the package directory, named `@tsnee/ears-checker`. It holds:
  - the generated `package.json`, with the usual npm metadata: `exports`, `types`, `sideEffects: false`, `engines` (Node 22 and newer), and the repository in the form npm's trusted publishing expects;
  - the linked JavaScript, and a source map with this repository's sources embedded (library sources stay public URLs);
  - a hand-written TypeScript declaration file (`index.d.ts`) that names every result type;
  - a short README;
  - the CC0 license.
- The package has no runtime dependencies.
- **Versions outside releases** move to the next minor version, so they sort after the released `0.1.0`: `0.2.0-SNAPSHOT` for the Maven artifacts, and `0.2.0-dev`, the npm form, for the package. Release versions are the same for both.
- Node tests (zio-test on Scala.js) cover the facade.
- **A smoke check** packs the package, lints it with publint and Are the Types Wrong, and installs it into an empty project. It type-checks a small TypeScript consumer under both Node and bundler module resolution, then runs that consumer on Node. CI runs it on every push and pull request, on Node 26 and on Node 22.
- The README and CLAUDE.md describe how to build the package.
- The roadmap moves out of CLAUDE.md, which keeps only lasting rules and facts, into a new `docs/roadmap.md`. The paragraph on the repository's extraction, which is history, leaves CLAUDE.md too, along with the exact tool versions, which `build.mill` records.
- **Not in this change:** publishing to npm. The release workflow stays unchanged. A later change will publish from it; the design records the plan: trusted publishing, behind its own approval.
- **Not in this change:** a Node build of the CLI. Markdown input uses commonmark-java, which is JVM-only.

## Capabilities

### New Capabilities

- `npm-package`: the `@tsnee/ears-checker` npm package. It covers:
  - the exported JavaScript API and its result shapes;
  - how argument errors are reported;
  - the TypeScript declarations;
  - the package's metadata, version, and source map.

### Modified Capabilities

- `continuous-integration`: CI also builds the npm package, lints it, and smoke-tests it from TypeScript and Node, on the oldest and newest Node versions it supports. If any of that fails, the run fails.
- `release-publishing`: the version outside releases becomes `0.2.0-SNAPSHOT`.
- `test-coverage`: the npm package's core, `ears.npm`, is measured like `ears` and `ears.cli`.

## Impact

- **`build.mill`:**
  - two new modules, neither `Published`, so the Maven Central release doesn't change:
    - `ears.npm`, the pure core of the API, built and measured on the JVM, with the package-assembly task;
    - `ears.npm.js`, the core plus the export, linked as an ES module;
  - the snapshot version becomes `0.2.0-SNAPSHOT`.
- **New sources:** `ears/npm/src/…` (the core) and `ears/npm/js/src/…` (the export), each with its tests.
- **New package files:** `ears/npm/package/`, holding `index.d.ts` and the README, and `ears/npm/smoke/consumer.ts`.
- **New script:** `scripts/check-npm-package`, the smoke check. It uses pinned versions of TypeScript, publint, and Are the Types Wrong from npm, as CI already does for the OpenSpec CLI.
- **Files that change:**
  - `.github/workflows/ci.yml`: one more step in `check`, and Node 22 plus the same step in `check-jdk17`;
  - `README.md`, `CLAUDE.md`, and `docs/worklog.md`.
  - New: `docs/roadmap.md`.
- **Dependencies:** no new Scala dependencies.
- **The `ears` library, `ears.js`, and `ears.cli`:** no code changes.
