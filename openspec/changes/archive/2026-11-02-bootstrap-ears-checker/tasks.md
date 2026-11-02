# Tasks

No spec, source file, or test changes in this change. If one seems
needed for the build to pass, stop and ask first.

## 1. Build

- [x] 1.1 Remove `object semantics` from `build.mill`; verify `./mill __.compile` passes and `./mill resolve __` lists only `ears` modules
- [x] 1.2 Add the `Published` trait (design Decision 1) to `ears`, `ears.js` and `ears.cli` with their artifact names; verify `./mill show ears.cli.artifactMetadata` (or the Mill 1.3.1 equivalent) gives `io.github.tsnee`, `ears-checker-cli_3`, `0.1.0-SNAPSHOT`
- [x] 1.3 Run `./mill __.test` and `./mill __.checkFormat`; verify both pass, including `ears.js` tests on Node and `OwnSpecsSuite` over this repository's specs

## 2. Repository files

- [x] 2.1 Replace `openspec/config.yaml`'s `context` with `yq`, describing this checker (Scala 3.10, Mill, JVM and Scala.js library plus ZIO CLI, generic example domains); verify `scripts/check-openspec` passes
- [x] 2.2 Write `README.md` (what EARS is, the library and the `ears-check` command line with an example, unreleased status, CC0); verify by reading it with `mq`
- [x] 2.3 Download the CC0 1.0 legal code (design Decision 3) to `LICENSE`; verify it starts with "Creative Commons Legal Code" and "CC0 1.0 Universal"
- [x] 2.4 Start `docs/worklog.md` with a dated line for the extraction and one for this bootstrap; verify by reading it

## 3. Local publish

- [x] 3.1 Run `./mill __.publishLocal`; verify `~/.ivy2/local/io.github.tsnee/` holds `ears-checker_3`, `ears-checker_sjs1_3` and `ears-checker-cli_3` at 0.1.0-SNAPSHOT
- [x] 3.2 Run `cs launch --java-opt --sun-misc-unsafe-memory-access=allow -M io.github.tsnee.ears.cli.Main io.github.tsnee:ears-checker-cli_3:0.1.0-SNAPSHOT -- --help`; verify it prints the usage line with `[<file>...]`

## 4. Wrap-up

- [x] 4.1 Run `scripts/check-openspec` and `./mill __.test` once more; verify both pass
- [x] 4.2 Audit the whole history and working tree against a term list: `git log --all -p` and `git log --all --format=%B` piped to `grep -i -E -f <the list>`, and `grep -r -i -E -f <the list> --exclude-dir=.git --exclude-dir=out .`; verify all three print nothing

## Workflow follow-up

- Commit the implementation, verify, archive (no spec sync: `skip_specs`), commit the archive, and push `main`.
- Tell the user, so the downstream project can switch its check to the published checker.
