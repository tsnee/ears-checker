# Design

## Context

Observations (2026-11-07):

- **README.md** is 257 lines: EARS, the command line (Coursier, the jar, Node, the native binary,
  options, inline code, encoding, exit status), JSON output, releases, the Scala library, the npm
  package, and a 40-line "Building" section for contributors. It names `0.1.1` in four places by
  hand.
- **What's available, and when:** 0.1.1 is on Maven Central (the shields.io Maven Central badge
  shows `v0.1.1`). The native binaries, the GitHub Release files, and the npm tarballs arrive
  with 0.2.0, which this theme (`batch-release-0.2.0`) ends by releasing. The npm packages are
  published by the owner after 0.2.0 (the roadmap's npm item), so `npm install` from the
  registry and `npx` don't work until then. `cs install` waits for the roadmap's Coursier app
  item; `cs launch` works today.
- **What's checked, by input** (the code, not the README, which doesn't say all of this):
  - Plain text: every nonblank line, trimmed, is one requirement.
  - Markdown: each sentence in a paragraph or list item that contains `shall` in any case outside
    inline code (`Extraction.keyword`). A sentence with only `should` isn't checked at all in
    Markdown, though it's a `weak-modal` finding in plain text. Block quotes are read; headings,
    code blocks, and HTML aren't. No parser here recognizes GitHub-style tables, so a table is
    read as a paragraph (the review found this; corrected 2026-11-07).
  - OpenSpec: the text of each added and modified requirement of a change, or every requirement
    of a spec; not scenarios.
  - The checker judges form only: one sentence, one template, the modal keyword, clause order and
    punctuation, an article before the system. It doesn't judge vague words ("quickly"),
    testability, or whether the requirement is right, and it reads English only.
- **Security reporting:** GitHub's private vulnerability reporting is off for the repository
  (`GET repos/tsnee/ears-checker/private-vulnerability-reporting` → `{"enabled":false}`). Issues
  are on; discussions are off.
- **Badges** (fetched today): GitHub's `ci.yml` badge for `main` and shields.io's license badge
  return SVGs; shields.io's `maven-central/v/io.github.tsnee/ears-checker_3` redirects to its
  Maven metadata badge, which shows `v0.1.1`.

## Goals / Non-Goals

**Goals:**
- A README whose first screen says what this is and how to run it, for each way users get it.
- An honest limits section.
- CONTRIBUTING.md for everything about working on the repository; SECURITY.md with a private
  way to report.

**Non-Goals:**
- Changing any behavior, or the Markdown filter (a sentence with only `should` stays unchecked:
  that's how prose that merely mentions requirements is skipped).
- The npm and Coursier items' own README updates: each changes the quick start when it lands.

## Decisions

### 1. The README's order

1. Title, one line on what it is, badges (CI, Maven Central, license; npm joins with the npm
   item).
2. **Quick start**: the shortest way per route, each with a version: a release binary (curl,
   chmod, cmark for Markdown), the jar (`java -jar`), Coursier (`cs launch`), npm (once
   published; the tarball from the release until then), and the library for Mill, sbt, and
   Maven. Then one example run.
3. **What it checks**: the six templates, a short list of the 14 kinds (the full table is in
   `docs/json-output.md`), and **Limits**, from the observations above.
4. **Command line**: options, input formats, profiles, output formats (text; JSON with a link),
   exit status, encoding, logging.
5. **Library** (Scala) and **JavaScript and TypeScript**, as now.
6. **Releases**: the changelog, release files, checking them (as now).
7. **Contributing**, **Security**, **License**: one line each, linking the files.

### 2. Written for 0.2.0

- This theme ends by releasing 0.2.0, so the README names 0.2.0 throughout (see Risks for how long it's
  ahead). What waits for a later item says so
  plainly ("not on npm yet: install the tarball from the release").
- The npm packages' own READMEs describe the published packages ("install it with `npm install
  @tsnee/...`, or from a release's tarball"), not "not on npm yet": the owner publishes 0.2.0
  from the release's tarballs, which carry those READMEs, so a "not yet" line would end up on
  npm. They're briefly ahead of npm instead, between the release and publishing.
- Versions in the README are bumped by hand at each release; CLAUDE.md's release steps gain that
  line, next to renaming `Unreleased`.

### 3. CONTRIBUTING.md

- From the README: building, the tests on each platform, coverage, the check scripts and what
  each needs, CI. New: changes go through OpenSpec with strict EARS requirements (the checker
  checks its own specs), each change's commits (plan, implementation, archive), the changelog's
  Unreleased section, and the checks to run before a change is done.
- Not a copy of CLAUDE.md: it links CLAUDE.md for the project's code style and conventions, which
  apply to everyone.

### 4. SECURITY.md

- **Reporting:** GitHub's private vulnerability reporting ("Report a vulnerability" on the
  Security tab), which the owner turns on in the repository's settings; no email address. The
  README and SECURITY.md say not to open a public issue for a vulnerability.
- **Supported versions:** the latest release only, while the version is 0.x.
- **Scope:** the CLI reads untrusted files (inputs are read whole, so a huge file uses memory:
  the roadmap's size limit); the native binary loads cmark from fixed system directories and
  never from the current directory; release files carry checksums and build-provenance
  attestations. What's out of scope: the classifier's verdicts.

### 5. Every example is a real run

- Each command and output in the README is run before the commit (jar, native binary, Node
  package, Coursier with 0.1.1 where 0.2.0 doesn't exist yet), and the design records which
  ones can't run until 0.2.0 or npm publishing.

## Risks / Trade-offs

- **[Docs ahead of releases]** From the merge until 0.2.0 is out (the tag's CI run, the owner's
  approval, the Central Portal publish and Maven Central's sync, and publishing the draft
  release: hours, perhaps a day), the quick start names files and versions that don't exist
  yet. Accepted.
- **[Two places to update at a release]** Versions in the README and the changelog; the release
  steps list both.

## Open Questions

- None. The owner turned on private vulnerability reporting (`{"enabled":true}`, checked
  2026-11-07) and agreed to writing the README for 0.2.0.
