## Files

| File | What it is | Needs |
| --- | --- | --- |
| `ears-check-{version}.jar` | The `ears-check` command line, with its dependencies | JDK 17 or later |
| `tsnee-ears-checker-cli-{version}.tgz` | The npm package `@tsnee/ears-checker-cli`: `npm install` gives the `ears-check` command | Node 22 or later |
| `tsnee-ears-checker-{version}.tgz` | The npm package `@tsnee/ears-checker`: `npm install` gives the classifier as a library, with TypeScript declarations | Node 22 or later |
| `ears-check-{version}-linux-x64` | `ears-check` as a native binary for Linux on x64 | glibc 2.35 or later (Ubuntu 22.04, Debian 12) |
| `ears-check-{version}-linux-arm64` | `ears-check` as a native binary for Linux on Arm | glibc 2.35 or later (Ubuntu 22.04, Debian 12) |
| `ears-check-{version}-macos-arm64` | `ears-check` as a native binary for macOS on Apple silicon | macOS 15 or later |
| `SHA256SUMS` | The SHA-256 of every other file |  |

The library is on Maven Central as `io.github.tsnee:ears-checker_3:{version}` (and `ears-checker_sjs1_3` for Scala.js), and the command line as `io.github.tsnee:ears-checker-cli_3:{version}`. Once the owner has checked and approved them, both npm packages are on npm too, as `@tsnee/ears-checker@{version}` and `@tsnee/ears-checker-cli@{version}`, with the same tarballs as here: `npm view @tsnee/ears-checker-cli@{version} dist.shasum` gives the SHA-1 that `shasum tsnee-ears-checker-cli-{version}.tgz` does, and prints nothing until then.

The jar here and the one on Maven Central are built from the same tagged commit, but in separate jobs, so they may not be identical byte for byte. The checksums and attestations below cover the files here.

### Checking the files

Each file was built and checked by this release's workflow run. Check a file before running it, in the directory holding it and `SHA256SUMS`, naming the file:

```sh
grep ' ears-check-{version}-linux-x64$' SHA256SUMS | sha256sum -c
```

(`shasum -a 256 -c` where there's no `sha256sum`.) That shows the download is intact, but not who built it, since `SHA256SUMS` comes from the same page. The attestation shows who: this repository's release workflow, for tag `v{version}`. Check it with the [GitHub CLI](https://cli.github.com):

```sh
gh attestation verify ears-check-{version}-linux-x64 --repo tsnee/ears-checker \
  --signer-workflow tsnee/ears-checker/.github/workflows/release.yml \
  --source-ref refs/tags/v{version}
```

### Running the files

Run the jar with `java -XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow -jar ears-check-{version}.jar`: the flag stops JDK 23 and later from warning about the Scala runtime's use of `sun.misc.Unsafe`, and older JDKs skip it. Install an npm package from npm with `npm install @tsnee/ears-checker-cli@{version}`, or a tarball from here with `npm install ./tsnee-ears-checker-cli-{version}.tgz`.

The native binaries read Markdown through cmark 0.30 or later, which they load when they run: `brew install cmark` on macOS, `apt install cmark` on Debian and Ubuntu. Plain text and OpenSpec JSON need nothing installed. Make a binary executable with `chmod +x` after downloading it.

The Coursier app installs these too: `cs install --channel https://raw.githubusercontent.com/tsnee/ears-checker/main/coursier/apps.json ears-check` gets this platform's native binary, or runs the jar from Maven Central on the JVM where there's none. Coursier keeps the binary as `.ears-check.aux` next to the `ears-check` it installs (by default in `~/.local/share/coursier/bin` on Linux and `~/Library/Application Support/Coursier/bin` on macOS); to check who built it, run the attestation command above on that file.

### macOS

The macOS binary isn't signed. macOS refuses to open an unsigned program that a browser downloaded ("Apple could not verify…"), because the browser marks the file as quarantined. Download it with `curl`, which doesn't mark it:

```sh
curl -fLO https://github.com/tsnee/ears-checker/releases/download/v{version}/ears-check-{version}-macos-arm64
```

Or, after checking its checksum and attestation as above, clear the mark from a file the browser downloaded (this tells macOS to open it without Apple's check):

```sh
xattr -d com.apple.quarantine ears-check-{version}-macos-arm64
```
