# Design

## Context

Observations (2026-11-08, Coursier 2.1.26 from Homebrew, macOS 27.2 on Apple silicon):

- **Descriptor fields** (Coursier's `RawAppDescriptor.scala` and
  get-coursier.io/docs/cli-appdescriptors): `repositories`, `dependencies`, `mainClass`,
  `javaOptions`, `launcherType` (`bootstrap` by default; also `scala-native`,
  `graalvm-native-image`, `prebuilt`, …), `prebuilt` (one URL template), `prebuiltBinaries` (a
  map from Coursier's platform names to URL templates), `name`. Templates take `${version}` and
  `${platform}`; the platform names are `x86_64-pc-linux`, `aarch64-pc-linux`,
  `aarch64-apple-darwin`, `x86_64-apple-darwin`, and `x86_64-pc-win32`. Unknown fields are ignored.
- **Fallback** (Coursier's `InstallDir.scala`): with no prebuilt binary found, launcher type
  `prebuilt` fails, `scala-native` builds the app natively on the user's machine, and
  `graalvm-native-image` falls back to a JVM launcher ("Some native launcher types fall back to a
  JVM launcher when no prebuilt").
- **Channels** (`cs install --help`): `--channel` takes the URL of a JSON file (an object from app
  names to descriptors), a JAR's `org:name`, or a local directory of `<app>.json` files.
- **Probes** (scratchpad, against 0.1.1, the latest CLI on Maven Central, which has no native
  binary):
  - A descriptor with `io.github.tsnee:ears-checker-cli_3:latest.release`, the main class, and
    both JVM options installed from a local directory channel as a 233 KB bootstrap launcher. It
    resolved 0.1.1, ran, printed no `Unsafe` warning on the local JDK, and its script carries both
    options.
  - The same descriptor from a single-file channel given as a `file://` URL installed and ran the
    same way.
  - With `launcherType: graalvm-native-image` and `prebuiltBinaries` naming release URLs that
    don't exist for 0.1.1, the install succeeded with a JVM launcher (29 MB, the jars inside),
    which ran.
  - With the `aarch64-apple-darwin` URL pointing (`file://`) at a copy of this branch's native
    binary named `ears-check-0.1.1-macos-arm64`, Coursier installed it as `.ears-check.aux`,
    byte-identical, behind a two-line `sh` wrapper, `ears-check`; it ran and reported its own
    version (`0.2.0-SNAPSHOT`), so the binary, not the Maven version, decides what runs. The file
    carries `com.apple.provenance` but no quarantine attribute.
- **Release files** (`docs/release-files.md`, `native.yml`): `ears-check-{version}-linux-x64`,
  `-linux-arm64`, and `-macos-arm64`, uncompressed, at
  `https://github.com/tsnee/ears-checker/releases/download/v{version}/`. Without cmark, the native
  binary's Markdown error says how to install it (`CmarkLibrary`).
- **CI:** no job installs Coursier's CLI (`coursier/cache-action` only caches).
  `coursier/setup-action`'s latest release is v3.1.0 (2026-11-03); v3.0.4 is 2026-10-01.
- **Maven Central** lists `ears-checker-cli_3` 0.1.0 and 0.1.1 (2026-11-08).
- **`cs update`** (probe, 2026-11-11, Coursier 2.1.26, this Mac): the CLI published as 9.0.0 and
  then 9.0.1 (`EARS_RELEASE_VERSION`) to a scratch Ivy repository in front of the descriptor's
  own, with `latest.release`. After installing at 9.0.0, `cs update --install-dir … ears-check`
  moved the JVM launcher from `"checker":"9.0.0"` to `"9.0.1"`, and the native install from the
  9.0.0 binary to the 9.0.1 file ("Updated ears-check (9.0.0 -> 9.0.1)").
- **The JVM launcher** Coursier writes (same day): a `#!/usr/bin/env sh` preamble that appends
  `-jar "$0"`, picks `$JAVA_HOME/bin/java` when it's executable (else `java`), and runs `exec
  "$JAVA_CMD"` with the descriptor's options and `$JAVA_OPTS`, followed by the jars.
- **Install directories** (get-coursier.io/docs/cli-install, 2026-11-11): `~/.local/share/coursier/bin`
  on Linux and `~/Library/Application Support/Coursier/bin` on macOS (the latter also seen on this
  Mac); `--install-dir` or `COURSIER_BIN_DIR` override them.
- **`coursier/setup-action` v3.0.4** (its source, read in the code review, 2026-11-11): with `jvm`
  empty and `JAVA_HOME` set, it changes neither `JAVA_HOME` nor the `java` on the PATH; it
  downloads Coursier's CLI (2.1.26) from Coursier's GitHub Release without checking a hash.

## Goals / Non-Goals

**Goals:**
- One `cs install` gives `ears-check` on every platform Coursier supports: native where a
  release has a binary, the JVM elsewhere; `cs update` moves it to the next release.
- CI proves the descriptor works with this build before a release ships it.

**Non-Goals:**
- Listing the app in Coursier's own channels (a pull request to `coursier/apps`): later, once
  the app has been used.
- Checksums for the downloaded binary: Coursier downloads it over HTTPS with no checksum field
  in the descriptor; the release's attestation can still check it (README).
- Windows and Intel macOS native binaries (roadmap).

## Decisions

### 1. A single-file channel at `coursier/apps.json`

- One JSON object, `{"ears-check": {…}}`, served from `main` by raw.githubusercontent.com. A
  directory channel can't be given by URL, and a JAR channel would have to be published to Maven
  Central with every change.
- The version is `latest.release`, so the file needn't change with each release and snapshots are
  never installed.

### 2. `graalvm-native-image` with `prebuiltBinaries`

- It's the only launcher type that uses a prebuilt binary and falls back to the JVM; the name
  only matters to Coursier's choice, which never runs `native-image` for this app. `scala-native`
  would build the app on the user's machine with no binary, needing clang; `prebuilt` would fail
  on Windows.
- `prebuiltBinaries`, not `prebuilt`, because this project's file names say `linux-x64` where
  Coursier's platform says `x86_64-pc-linux`.
- `javaOptions` carries the two flags for the JVM launcher. The native binary takes none.
- Timing: Maven Central and the GitHub Release are published by hand, one after the other (CLAUDE.md
  Releasing step 6). Installed between the two, the app falls back to the JVM, silently; it
  works, and `cs update` after the next release picks up a binary. The release steps say to
  publish the draft release first.

### 3. `scripts/check-coursier-app`, the third `cli-cases` subject

- The descriptor in the repository is the one checked: the script changes only where things come
  from, with `jq`, never its repositories, launcher type, main class, or options, so a broken
  field fails the check. It first requires the dependency to be exactly
  `io.github.tsnee:ears-checker-cli_3:latest.release`.
- JVM mode (default): `./mill ears.publishLocal + ears.cli.publishLocal` puts this build's CLI in
  the local Ivy repository; the copy puts `ivy2Local` in front of the descriptor's repositories,
  asks for this build's `publishVersion` in place of `latest.release`, and makes every download
  address a `file://` path that doesn't exist, so the JVM fallback is what's tested. It installs
  into a temporary directory with `--default-channels=false`, checks that the result has the JVM
  launcher's preamble, then compares the command with the jar on `cli-cases`. The cases run the
  launcher with `JAVA_HOME` and `JAVA_OPTS` unset, so it uses the `java` on the PATH, as the jar
  does.
- Native mode (`--binary <file> --jar <jar>`, in the native recheck jobs): the current platform's
  address becomes `file://` of a copy of the binary named as the release names it; the script
  checks that the installed command's `.ears-check.aux` is byte-identical to it (a JVM launcher
  fails here), then runs `cli-cases`. The rest of the descriptor still needs the CLI on a
  repository, so native mode uses `publishLocal` too.
- Both modes first check the addresses themselves: each of the three platforms' template,
  with `${version}` filled in, must be the release URL of that platform's file.
- CI: JVM mode in `check` (JDK 27, against the jar the job keeps for a release) and `check-jdk17`
  (JDK 17); native mode in each native recheck job, which now sets the release version on a tag
  so it publishes at the tag's version. Coursier's CLI comes from `coursier/setup-action`, pinned
  by commit like the other actions; v3.0.4, since v3.1.0 was 8 days old when pinned. Since it and
  the check's Mill run download tools unchecked, those steps come after a job's release files are
  uploaded, so nothing they run can change what ships.

### 4. Docs

- README: the Coursier section becomes `cs install --channel … ears-check` and `cs update
  ears-check`, says which platforms get the native binary (and that it needs cmark for Markdown),
  where Coursier keeps it, and how to check the installed binary's attestation.
  `docs/release-files.md` names the channel.
- CLAUDE.md and CONTRIBUTING.md list the script with the other checks; Releasing step 6 says to
  publish the draft release before the Central Portal deployment. After 0.2.0 is published, its
  release change installs the app from `main` on this Mac and records the result.

### 5. Intel macOS and Windows in CI (added 2026-11-11, the owner's choice)

- Neither has a native binary, so both test the case the release depends on there: a platform
  with no entry in `prebuiltBinaries` gets the JVM launcher. Docker can't stand in (it runs
  Linux; there are no macOS containers, and Windows containers need a Windows host); GitHub's
  `macos-15-intel` and `windows-latest` runners can, free for this public repository.
- No Mill there: `check` uploads its published `ears` and `ears.cli` (its `~/.ivy2/local`
  entries) and the jar it compared with as an artifact, and the two jobs install from it with
  `--repo <dir>`, which takes the version from the repository instead of from Mill.
- Intel macOS runs every case, like the Linux jobs, on JDK 25: Temurin has no JDK 27 for macOS
  on x64 (Adoptium's API, 2026-11-11: JDK 25 has x64 and aarch64 builds for macOS, JDK 27
  aarch64 only). Windows runs JDK 27 and `--cases smoke` (help, a passing file, a failing file,
  JSON output): the shared cases' unreadable file (`chmod 000`) and C-locale case (`LC_ALL=C`)
  don't hold on Windows, and they need the OpenSpec CLI, which that job doesn't install.
- The script runs under the runner's Git Bash. From Coursier 2.1.26's sources (read in the code
  review, 2026-11-11): with no `prebuiltBinaries` entry for `x86_64-pc-win32`, Coursier writes
  its JVM launcher as `ears-check.bat`, a batch preamble with the descriptor's JVM options as
  text and the launcher jar appended, which ends with `exit /B %ERROR_CODE%`; a native binary
  would be `.ears-check.aux.exe`. The script turns paths into Windows form (`cygpath -m`) for
  `file:` addresses and `cs`, runs Windows' jq (a native build) with `-b` so it writes LF line
  endings, keeps Git Bash from rewriting the URL arguments (`MSYS2_ARG_CONV_EXCL`), and prints
  the descriptor it installs.
- Not tested before CI: the Windows branch (this Mac has no Windows and no PowerShell); its
  first run is the test, and a failure there is fixed before archiving.

## Risks / Trade-offs

- **[Coursier changes the fallback]** A later Coursier could stop falling back for
  `graalvm-native-image`. The check would catch it only for the version CI installs; the README
  keeps the jar route.
- **[Native without cmark]** A user who installs on Linux or macOS gets the native binary, which
  needs cmark for Markdown; its error says how to install it, and the README says so up front.
- **[Fewer cases on Windows]** Windows runs four cases, not all of them; Intel macOS runs all.
  Windows on Arm (`windows-11-arm`) isn't checked.
- **[`main` serves the channel]** A change to `coursier/apps.json` reaches users as soon as it
  merges. CI checks it on every pull request first.
- **[Unchecked downloads in CI]** Coursier's CLI comes without a hash check, and the native recheck
  jobs now run Mill. Both come after the jobs' release uploads, and the jobs keep their read-only
  token and no secrets.

## Open Questions

- None.
