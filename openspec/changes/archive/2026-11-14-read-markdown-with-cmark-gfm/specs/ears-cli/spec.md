## REMOVED Requirements

### Requirement: Missing cmark

**Reason**: The native checker compiles cmark-gfm in, so it can always read Markdown and never finds cmark missing.

**Migration**: None; `ears-check notes.md` on a system without cmark reads the file instead of exiting with status 2.

### Requirement: Libraries tried for Markdown

**Reason**: The native checker loads no Markdown library, so there are no attempts to list.

**Migration**: Log readers that looked for the `attempts` detail on a Markdown input's error entry no longer find one; a Markdown input that can be read has no error entry.
