## REMOVED Requirements

### Requirement: A library planted in the current directory

**Reason**: The native binary compiles cmark-gfm in and loads no library when it runs, so there's no library a planted file could replace.

**Migration**: None; the native jobs drop the planted-library check, and `cli-native-binary`'s "System libraries only" still checks what the binary links.

### Requirement: Pinned Node CLI install

**Reason**: The CLI npm package has no dependencies (`cli-npm-package`'s "No dependencies in the CLI package"), so the check installs nothing beyond the packed package.

**Migration**: None; the Node CLI check installs the packed package alone.
