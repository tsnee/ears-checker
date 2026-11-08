# Security

## Reporting a vulnerability

Please report it privately, through GitHub: on this repository's **Security** tab, choose
**Report a vulnerability** ([direct link](https://github.com/tsnee/ears-checker/security/advisories/new)).
Don't open a public issue for it. Say what's affected (the library, the command line on the JVM,
Node, or natively, the npm packages, or the release workflow), the version, and how to reproduce
it.

This is a one-person project, so replies can take a few days. A confirmed problem is fixed in a
new patch release, with a GitHub security advisory and a line in the
[changelog](CHANGELOG.md).

## Supported versions

While ears-checker is at 0.x, only the latest release gets fixes: 0.2.x, from 0.2.0. Upgrade to
it rather than waiting for a fix to an older version.

## What's in scope

- **The command line reading untrusted files.** `ears-check` reads files and standard input as
  UTF-8 text, Markdown, or OpenSpec JSON, and writes findings and log lines. Escaping problems
  (a log line or output that another tool could misread), or anything that makes it read or
  write a file it wasn't given, are in scope. It reads each input whole, so a very large input
  uses a lot of memory; that's known (a size limit is on the [roadmap](docs/roadmap.md)) and not
  a vulnerability by itself.
- **The native binary loading cmark.** For Markdown, the native binary loads an installed cmark
  0.30 or later when it runs, by absolute path from the absolute entries of
  `LD_LIBRARY_PATH` or `DYLD_LIBRARY_PATH`, Homebrew's, MacPorts', and the system's library
  directories (and on Linux, the dynamic loader's own search for `libcmark.so`). It never names
  the current directory itself, but today the system's loader can still search it when
  `LD_LIBRARY_PATH` or `DYLD_LIBRARY_PATH` has an empty or relative entry; closing that is on the
  [roadmap](docs/roadmap.md) for 0.2.0. Any other way to make it load a library from somewhere
  else is in scope.
- **The release files.** Each GitHub Release carries the files its release run built and
  checked, a `SHA256SUMS` file, and a build-provenance attestation for each file. Check them as
  [docs/release-files.md](docs/release-files.md) says. A way to get a file into a release that
  the release workflow didn't build is in scope, as is a problem in the workflows themselves
  (`.github/workflows/`).
- **The npm packages and Maven Central artifacts.** What they contain, and their declared
  dependencies.

Out of scope: the classifier's verdicts (a requirement judged wrongly is an ordinary bug, for an
issue), and vulnerabilities in a dependency that ears-checker doesn't make reachable.
