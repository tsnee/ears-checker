# Design

## Permanent decisions (review these first)

Once 0.1.0 is published these can't change for that version, and the coordinates and license can't change for any later one without confusing users. Everything else in this design can be revised later.

| Decision | Value |
| --- | --- |
| Group | `io.github.tsnee` |
| Library (JVM) | `ears-checker_3` |
| Library (Scala.js) | `ears-checker_sjs1_3` |
| Command line | `ears-checker-cli_3`, main class `io.github.tsnee.ears.cli.Main` |
| First version | `0.1.0` (0.x: the API may change in any minor version) |
| License | CC0 1.0 (`CC0-1.0`) |
| POM name, URL, SCM | artifact name; `https://github.com/tsnee/ears-checker` |
| Developer | `tsnee`, Tom Snee, `https://github.com/tsnee` |
| Descriptions | library: "Classifies requirement sentences against the EARS templates"; CLI: "EARS checker for plain text, Markdown, and OpenSpec JSON" |
| Runtime | Java 17 bytecode; Scala 3.10 (users need Scala 3.10 or later); Scala.js 1.22 |
| Signing key | ed25519, fingerprint `B7D3EA3ABB9B7F5B09AEF10FFDAF9649810B085A`, expires 2028-11-01 |

All of these are already in `build.mill` (`Published`, the three modules) and were checked with `./mill __.publishLocal` on 2026-11-03: each module has a jar, POM, sources jar, and a docs jar of about 400 entries.

## Context

See proposal.md for why and `specs/release-publishing/spec.md` for the required behavior. What's in place:

- The Central Portal account with `io.github.tsnee` verified; the public key on `keyserver.ubuntu.com` and `keys.openpgp.org`.
- The `release` environment: only `v*` tags may use it, the owner must approve each job, and it holds `MILL_SONATYPE_USERNAME`, `MILL_SONATYPE_PASSWORD`, `MILL_PGP_SECRET_BASE64`, and `MILL_PGP_PASSPHRASE`.
- Mill 1.3.1's `mill.javalib.SonatypeCentralPublishModule/publishAll` (read from its sources): it publishes every `PublishModule`, reads exactly those four variables, signs in-process (no `gpg` command), and uploads one bundle. Its `shouldRelease` defaults to **true**, which is the Portal's `AUTOMATIC` mode: the upload is published without review. `--shouldRelease false` selects `USER_MANAGED`.

## Goals / Non-Goals

**Goals:**

- Releasing is: merge, push a tag, approve, review in the Portal, publish. No release commits.
- Nothing becomes public without two deliberate clicks by the owner (the job approval and the Portal's Publish).
- The publishing secrets are only ever in a job that runs after approval, on a version tag, from a commit on `main` that passed every check.

**Non-Goals:** see the proposal (GitHub Releases, binaries, Coursier app, npm, snapshots on Central).

## Decisions

### 1. Version from the tag, through one environment variable

```scala
trait Published extends PublishModule {
  def publishVersion = Task.Input {
    Task.env.get("EARS_RELEASE_VERSION").filter(_.nonEmpty) match {
      case None => "0.1.0-SNAPSHOT"
      case Some(v) if v.matches("""\d+\.\d+\.\d+""") => v
      case Some(v) => Task.fail(s"EARS_RELEASE_VERSION must be <major>.<minor>.<patch>, not '$v'")
    }
  }
  …
}
```

`Task.Input` re-reads the environment on every run, so a cached snapshot version can't leak into a release. The workflow sets the variable from the tag after checking it matches `v<major>.<minor>.<patch>`. The user chose this over editing `build.mill` for each release (2026-11-03).

The build also rejects anything but `<major>.<minor>.<patch>` and treats an empty value as unset (code review finding 6, 2026-11-03): a stray `export EARS_RELEASE_VERSION=…` followed by `./mill __.publishLocal` would otherwise put a release version into `~/.ivy2/local`, which the downstream project reads before Central.

### 2. Check, then publish; build before the secrets

```text
on: push: tags: ['v[0-9]+.[0-9]+.[0-9]+']

tag     (no secrets): checkout (full history) -> tag format and "on main" checks
checks  (no secrets; needs: tag): uses ./.github/workflows/ci.yml
          -> its check and check-jdk17 jobs, exactly as on main
publish (needs: tag, checks; environment: release)
  checkout -> set EARS_RELEASE_VERSION from the tag
  -> ./mill __.publishArtifacts                       (no secrets)
  -> ./mill mill.javalib.SonatypeCentralPublishModule/publishAll --shouldRelease false
                                                      (secrets; signs and uploads only)
```

The approval prompt appears only after `tag` and `checks` pass, so the owner approves a release that is known to be green, and the secrets exist only in `publish`'s last step.

Changes from code review (2026-11-03, accepted by the owner):

- `checks` calls `ci.yml` (which gained a `workflow_call` trigger) instead of copying its steps, so the release reruns exactly what CI runs, JDK 17 job included, and the two can't drift (findings 3, 4). The release workflow's concurrency group is `maven-central-<ref>`: `ci.yml`'s group evaluates to `Release-<ref>` when called from here, and GitHub compares group names case-insensitively, so `release-<ref>` would have deadlocked.
- The build runs in its own step before the secrets are set, so compiler plugins, dependencies, and scaladoc never run beside the signing key; `publishAll` then reuses the built artifacts (finding 1). Checked with a local dry run: the second step compiled nothing.

Kept as designed, with the owner's agreement:

- `publish` rebuilds from the same commit instead of downloading the jars `checks` tested (finding 2): the same commit, pinned dependencies, and compiler build the same artifacts, and passing jars between jobs would mean trusting an artifact store.
- "On main" is ancestry, not "at the head of main" (finding 5): only the owner can push `v*` tags, `checks` reruns every check on the tagged commit, and the owner sees the version and files in the Portal before publishing.

"On main" is `git merge-base --is-ancestor "$GITHUB_SHA" origin/main` after a full-history checkout. The tag name reaches shell steps only through `env:`, never as `${{ }}` inside `run:`, so a crafted tag can't inject commands (the trigger's tag filter also limits it to digits and dots).

Alternative: one job in the `release` environment. Simpler, but the owner would approve before knowing whether the checks pass, and the secrets would sit in the job while tests run.

### 3. Review in the Portal, always

`--shouldRelease false` is fixed in the workflow. The first run is a rehearsal: inspect the deployment, then **Drop** it; rerun the `publish` job (approving again) for the real upload, then **Publish**. A dropped deployment doesn't use up the version.

### 4. No caches where the secrets are

`publish` restores no cache: the job that holds the secrets builds everything from a clean checkout, so it can't pick up anything a cache holds. `checks` uses `ci.yml`'s Coursier and Mill caches as CI does; a tag run can only restore caches saved on the tag itself or on `main`, never a pull request's, and `checks` holds no secrets.

### 5. Same rules as CI

Every action pinned to a commit SHA (the same pins as `ci.yml`, kept current by the existing Dependabot config), top-level `permissions: contents: read`, `persist-credentials: false`, job timeouts, and `concurrency` per tag without cancelling.

### 6. A tag ruleset for `v*`

A repository ruleset targeting `refs/tags/v*` blocks creating, updating, and deleting matching tags for everyone except repository admins (the owner). The `release` environment already refuses other refs; the ruleset stops anyone else from creating a release tag at all.

### 7. Scala.js source references point at GitHub

Scala.js IR (`.sjsir`) records each source file's URI for source maps, and by default that's the absolute `file:` path of the build machine (found in task 2.2: 62 of `ears.js`'s IR files named `/Users/…/ears-checker/…`). `ears.js` adds `-scalajs-mapSourceURI:<repository root>-><target>`, with the target taken from `publishVersion`:

- a release: `https://raw.githubusercontent.com/tsnee/ears-checker/v<version>/`, so a browser's source maps load the released sources;
- a snapshot: no target, which makes the references relative (`ears/src/…`).

The user chose this (2026-11-03) over relative paths in releases too (no machine paths, but source maps can't fetch anything) and leaving the CI runner's `/home/runner/…` paths in place. Checked locally: neither build has a `file:` reference left.

Kept as designed (code review finding 7): the URL is built from the version, not the tag, but after Decision 1's validation only the release workflow sets a version, and only as the tag without its `v`, so the URL always names the tag being released.

## Risks / Trade-offs

- [The wrong thing is published] → `tag` and `checks` must pass, then two owner actions; the Portal shows every file before Publish; and the rehearsal exercises the whole path once with nothing kept.
- [A secret leaks through a log] → GitHub masks secret values; Mill marks the passphrase secret in its own logging; no step echoes the environment.
- [A release workflow bug on `main` publishes from an untested path] → Its first real use is the rehearsal, which ends in Drop.
- [Central sync delay] → After Publish, Central can take up to about 30 minutes; the outside check waits for it.
- [Mistakes after publishing] → Versions can't be deleted; the fix is a new patch version.

## Migration Plan

After 0.1.0 is on Central, the downstream project switches from `~/.ivy2/local` to `0.1.0`, and CLAUDE.md's `publishLocal` instruction can be removed in a later change.
