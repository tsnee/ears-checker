With `--sourcepos`, a setext heading that isn't at the end of the document gets an end position on the line after its underline, which belongs to the next block (or is blank).

Input (`g.md`):

```
The calendar shall heading
---
The report shall print.
```

`cmark --sourcepos -t xml g.md` (cmark 0.31.2, Homebrew, macOS):

```xml
<heading sourcepos="1:1-3:23" level="2">
<paragraph sourcepos="3:1-3:23">
```

The heading ends at `3:23`, the end of the paragraph's line, overlapping the paragraph. With a blank line after the underline (`h.md`: the same, with an empty line 3), it ends at `3:0`:

```xml
<heading sourcepos="1:1-3:0" level="2">
<paragraph sourcepos="4:1-4:23">
```

At the end of the document it's right: `The calendar shall heading\n---\n` gives `1:1-2:3`.

Expected `1:1-2:3` in all three cases: the underline's line and last column. commonmark.js gives `[[1,1],[2,3]]` for both `g.md` and `h.md`, and commonmark-java 0.31.0 gives source spans on lines 1 and 2 only.

Not the same as #141 (whether the underline is included at all, which it is, as expected) or #296 (inline positions).
