# Proposal

## Why

CI run 42516759834 (2026-11-08, commit `f99ef39`, which changed only `openspec/`) failed at its
Compile step: Scala Native 0.5.12's compiler plugin threw `tail of empty list` while compiling
`ears.markdown.native.test`, sources that had compiled in the run before. The plugin keeps its
definitions in a global cache with no locking, so two Scala Native compiles in Mill's one JVM can
corrupt each other's compiler state, and that run had three of them compiling when it crashed
(design). It can fail any run, a release run included, and local builds too. The owner chose to
compile the native modules one at a time until upstream fixes it, and to report it upstream
(2026-11-08).

## What Changes

- CI's Compile step becomes two: everything but the Scala Native modules in parallel
  (`./mill '__:^ScalaNativeModule.compile'`), then the Scala Native modules one at a time
  (`./mill -j 1 '__:ScalaNativeModule.compile'`). The Test step then finds them compiled.
- The checks before a change is done (CLAUDE.md, CONTRIBUTING.md) compile the native modules the
  same way before `./mill __.test`.
- A comment on the open scala-native issue with the same crash (#4950), with the cause, the
  evidence, and a suggested fix (locking `ContextCached.get`), drafted in this change for the
  owner to post or approve.
- When a scala-native release fixes it, a later change drops the workaround (roadmap).

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `continuous-integration`: the Scala Native modules are compiled one at a time.

## Impact

- `.github/workflows/ci.yml` (the `check` job's Compile step).
- CLAUDE.md, CONTRIBUTING.md, `docs/roadmap.md`, `docs/worklog.md`, and the cost measured in
  `docs/performance.md`.
- No change to the build, the code, or what is released.
