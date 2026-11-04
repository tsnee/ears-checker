# Tasks

Groups 1–4 happen on branch `publish-to-maven-central` before it
merges; group 5 needs the release workflow on `main`, so it happens
after the merge, and the archive goes in a small follow-up pull
request. Pushing branches and tags happens in the owner's shell;
Claude opens pull requests, reads runs with `gh`, and never handles
the secrets. Nothing in groups 1–4 writes a non-SNAPSHOT version to
`~/.ivy2/local`, which the downstream project reads.

## 1. Version from the tag

- [x] 1.1 Change `Published.publishVersion` in `build.mill` to design Decision 1's `Task.Input`; verify `./mill show ears.publishVersion` (and for `ears.js` and `ears.cli`) prints `0.1.0-SNAPSHOT` without `EARS_RELEASE_VERSION` or with it empty, `0.1.0` with `EARS_RELEASE_VERSION=0.1.0`, and fails for `0.1.0-rc1`, `v0.1.0`, and `1.2`, and that `./mill __.compile` and `./mill __.checkFormat` pass

## 2. Artifact inspection (nothing uploaded)

- [x] 2.1 With `EARS_RELEASE_VERSION=0.1.0`, build every module's POM (`./mill show <module>.pom`); verify with an XML-aware tool (`yq -p xml`) that each has the design's permanent values (group, artifact, version `0.1.0`, name, description, URL, CC0-1.0 license, developer, SCM) and that no dependency is a `-SNAPSHOT`; record the dependency list in the change's notes for the owner's review
- [x] 2.2 List the contents of each module's jar, sources jar, and docs jar from `./mill show <module>.jar`, `<module>.sourceJar`, and `<module>.docJar`; verify the jars hold only `io/github/tsnee/**`, `META-INF/`, and (for Scala.js) `.sjsir` files, the sources jars only this repository's sources, and the docs jars are non-empty, with no absolute paths from this machine in any of them
- [x] 2.3 Map the Scala.js IR's source references (design Decision 7) in `ears.js`; verify no `.sjsir` file in `ears.js.jar` has a `file:` reference, built as a snapshot (references start `ears/src/`) and with `EARS_RELEASE_VERSION=0.1.0` (references start `https://raw.githubusercontent.com/tsnee/ears-checker/v0.1.0/`), and that `./mill ears.js.test` passes
- [x] 2.4 Run the CLI from the local `0.1.0-SNAPSHOT` publish with `cs launch --java-opt -XX:+IgnoreUnrecognizedVMOptions --java-opt --sun-misc-unsafe-memory-access=allow io.github.tsnee:ears-checker-cli_3:0.1.0-SNAPSHOT -- --help` on Temurin 17 and on JDK 27; verify both print the usage line

## 3. Release workflow and repository settings

- [x] 3.1 Write `.github/workflows/release.yml` as in design Decisions 2–5 (tag filter, `tag` and `checks` (calling `ci.yml`) then `publish` with `environment: release`, building before the secrets are set, `--shouldRelease false`, no caches in `publish`, the same SHA pins as `ci.yml`, `contents: read`, `persist-credentials: false`, the tag passed to shells only through `env:`); verify `yq` parses it, `actionlint` reports nothing, every `uses:` equals a pin in `ci.yml`, no `run:` contains `${{`, and a local dry run of the two publish steps with a throwaway key compiles nothing in the upload step
- [x] 3.2 Create the `v*` tag ruleset (design Decision 6) with `gh api`; verify `gh api repos/tsnee/ears-checker/rulesets` lists it, active, targeting tags `refs/tags/v*`, with creation, update, and deletion rules and admins as the only bypass
- [x] 3.3 Update the README (Maven and Mill dependency lines and `cs launch` for `0.1.0`, marked "after 0.1.0 is published", above the build-from-source steps) and CLAUDE.md (a short "Releasing" section: merge, tag, approve, review in the Portal, publish); verify by reading both with `mq`
- [x] 3.4 Run `./mill __.test` and `scripts/check-openspec`; verify both pass and `git status` shows no change outside `build.mill`, `.github/`, `README.md`, `CLAUDE.md`, `docs/worklog.md`, and this change; add a dated line to `docs/worklog.md`

## 4. Reviews before merging

- [x] 4.1 Run `/security-review` and a local `/code-review` in a subagent over the branch's diff against `main`; report every finding to the owner, fix the ones the owner accepts, and record the rest with the owner's reason in the pull request description
- [x] 4.2 After the owner pushes the branch and Claude opens the pull request, verify `check` and `check-jdk17` pass and that no release run started (no tag was pushed)

## 5. First release (after the pull request merges)

- [x] 5.1 The owner pushes tag `v0.1.0` on the merge commit; verify a release run starts, `tag` and `checks` (both `check` and `check-jdk17`) pass, and `publish` waits for approval with no step run
- [x] 5.2 Rehearsal: the owner approves; verify the log shows a `USER_MANAGED` upload of version `0.1.0` that passed validation, and the owner sees the deployment in the Portal with all three modules' files and signatures; the owner drops it
- [x] 5.3 The owner reruns the `publish` job and approves; verify the upload passes validation again; the owner reviews and clicks Publish
- [x] 5.4 Check from outside once Central shows 0.1.0 (up to about 30 minutes): with an empty Coursier cache in the scratchpad, `cs fetch` resolves all three artifacts at `0.1.0` from Maven Central, `cs launch` runs the CLI on Temurin 17 and JDK 27, and one downloaded `.asc` verifies against the key fetched from `keyserver.ubuntu.com` in a throwaway gpg home

## Workflow follow-up

- Commit the plan, then the implementation, on branch `publish-to-maven-central`; the owner squash-merges after group 4.
- After group 5: verify, archive (syncing `release-publishing` into `openspec/specs/`), and land the archive through its own small pull request.
- Tell the owner the downstream project can switch to `io.github.tsnee:ears-checker-cli_3:0.1.0`; CLAUDE.md's `publishLocal` note can then go in a later change.
