# Security

## Reporting a vulnerability

Please report it privately, through GitHub: on this repository's **Security** tab, choose **Report a vulnerability** ([direct link](https://github.com/tsnee/ears-checker/security/advisories/new)). Don't open a public issue for it. Say what's affected (the library, the command line on the JVM, Node, or natively, the npm packages, or the release workflow), the version, and how to reproduce it.

This is a one-person project, so replies can take a few days. A confirmed problem is fixed in a new patch release, with a GitHub security advisory and a line in the [changelog](CHANGELOG.md).

## Supported versions

While ears-checker is at 0.x, only the latest release gets fixes: 0.2.x, from 0.2.0. Upgrade to it rather than waiting for a fix to an older version.

## What's in scope

- **The command line reading untrusted files.** `ears-check` reads files and standard input as UTF-8 text, Markdown, or OpenSpec JSON, and writes findings and log lines. Given a directory, it reads only the regular `.md` and `.txt` files under it, follows no symbolic link it finds there, and skips hidden entries and anything that isn't a file or a directory, so a crafted repository can't lead it out of the directory, loop it, or block it on a named pipe. Every output (text, JSON, messages on standard error, log lines) writes text from the input with each character outside the English character set (printable ASCII, Latin-1 without the soft hyphen, the combining accents, and typographic punctuation such as dashes and curly quotes) as `\uXXXX`, so no input can drive a terminal, reorder or hide text, or pass a look-alike letter for a Latin one; the first of two number signs is escaped too, and a text-output line never starts with a colon or white space, so no input can send a CI system such as GitHub Actions a command. Output isn't escaped for HTML or Markdown: a program that renders it does that. No input is read past a size limit (10 MiB by default, at most 100 MiB, set with `--max-input-bytes`), so a large input or a standard input that never ends stops there. Escaping problems (output another tool could misread), a way to make it read more of an input than the limit, or anything that makes it read or write a file it wasn't given, are in scope. Memory within the limit is known to grow with the input, mostly in the Markdown parser, and isn't a vulnerability by itself.
- **The Markdown parser.** Every build reads Markdown with cmark-gfm, vendored unedited and built from source: compiled into the native binary, and run as a WebAssembly module in the jar and the npm package, which keeps a memory error inside the module's own memory. The native binary loads no library for it. A crash, a hang, or memory corruption that crafted Markdown under the size limit can cause is in scope.
- **The release files.** Each GitHub Release carries the files its release run built and checked, a `SHA256SUMS` file, and a build-provenance attestation for each file. Check them as [docs/release-files.md](docs/release-files.md) says. A way to get a file into a release that the release workflow didn't build is in scope, as is a problem in the workflows themselves (`.github/workflows/`).
- **The npm packages and Maven Central artifacts.** What they contain, and their declared dependencies.

Out of scope: the classifier's verdicts (a requirement judged wrongly is an ordinary bug, for an issue), and vulnerabilities in a dependency that ears-checker doesn't make reachable.
