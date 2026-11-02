# Work log

- 2026-11-01: Extracted the EARS checker, with its history, from the repository it was built in,
  and removed what belonged to that repository from the files and the history.
- 2026-11-02: bootstrap-ears-checker: dropped the leftover module from the build, made the three
  modules publishable as 0.1.0-SNAPSHOT under CC0 1.0, rewrote the OpenSpec context, added the
  README, LICENSE, and this log, and published locally to `~/.ivy2/local`.
- 2026-11-02: restore-openspec-check-spec: restored the OpenSpec gate's six requirements from the
  archived changes, confirmed each against the script and hook in a scratch copy, and corrected
  one scenario whose "should" example strict validation rejects before EARS checking runs.
  Planned add-github-actions-ci.
- 2026-11-02: add-github-actions-ci: added the CI workflow (format, compile, test, and the OpenSpec
  check on Linux, with SHA-pinned published actions and a read-only token) and weekly grouped
  Dependabot updates for the pins; linted it with actionlint and checked each pin against its tag.
  Confirmed on GitHub: pull request and push runs, cache hits, and a format failure that stops
  the run; archived it and synced the continuous-integration spec.
- 2026-11-02: Reworded the files that named the repository this one was extracted from; the
  history is unchanged.
