# Tasks

No change to the build, sources, or tests. JDK 17 is Temurin 17 from
Coursier: `cs java-home --jvm temurin:17`, put first on the PATH for a
run. Pushing, merging, and opening pull requests' branches happen in
the user's shell; Claude opens pull requests and reads runs with `gh`.

## 1. Check script

- [ ] 1.1 Witness first: run `scripts/check-openspec` with Temurin 17 first on the PATH; verify it fails with `Unrecognized option: --sun-misc-unsafe-memory-access=allow` and a nonzero exit status
- [ ] 1.2 Start the jar in `scripts/check-openspec` with `-XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow` and a comment saying why (design Decision 1); verify it exits 0 with "every requirement passes" on Temurin 17, and on JDK 27 that its output has no `WARNING:` line mentioning `sun.misc.Unsafe`
- [ ] 1.3 In a scratch copy of the repository with an active change whose requirement reads "The scheduler MUST list upcoming events.", run the script on Temurin 17; verify it reports the weak-modal finding and exits nonzero, and that a `java` wrapper on the PATH logs exactly one `-jar` start (the single-run requirement)

## 2. CI on JDK 17

- [ ] 2.1 Add the `check-jdk17` job to `.github/workflows/ci.yml` as in design Decision 3 (Temurin 17, the same pins and OpenSpec CLI version as `check`, 30-minute timeout, `persist-credentials: false`), and correct the comment on `check`'s JDK step; verify `yq` lists both jobs and `actionlint` reports nothing
- [ ] 2.2 After the user pushes branch `choose-jvm-flag-by-jdk` and Claude opens a pull request, read the run with `gh`; verify both jobs succeed and `check-jdk17`'s setup-java step installed Java 17
- [ ] 2.3 Failure check: the user pushes branch `ci-jdk17-failure-check` (from the pull request branch, with `-XX:+IgnoreUnrecognizedVMOptions` removed from the script) and Claude opens a draft pull request; verify `check-jdk17` fails at "Check OpenSpec" with `Unrecognized option` in its log while `check` succeeds; then Claude closes the pull request and deletes the branch
- [ ] 2.4 After the pull request merges, add `check-jdk17` to `main`'s required status checks with `gh api`; verify the branch protection lists both `check` and `check-jdk17`

## 3. Docs

- [ ] 3.1 Use design Decision 1's command line in the README's `ears-check` alias, replacing the "needs JDK 23 or newer; leave it out" note with one sentence on the two options, and in CLAUDE.md's run instructions; drop the JVM-flag item from CLAUDE.md's Likely next steps; verify by reading both with `mq`, and that the README alias runs the jar on Temurin 17 and on JDK 27

## 4. Wrap-up

- [ ] 4.1 Run `./mill __.test` and `scripts/check-openspec`; verify both pass and `git status` shows no change outside `scripts/`, `.github/`, `README.md`, `CLAUDE.md`, `docs/worklog.md`, and this change
- [ ] 4.2 Add a dated line to `docs/worklog.md`; verify by reading it with `mq`

## Workflow follow-up

- Commit the plan, then the implementation, on branch `choose-jvm-flag-by-jdk`.
- Verify and archive on the branch (syncing `openspec-check` and `continuous-integration`) before the pull request merges, so the squash commit holds the plan, implementation, and archive.
