# JSON output

`ears-check --output json` writes one JSON document to standard output for the whole run, every
input together, for programs to read: CI annotations, editors, scripts. This page is the
document's reference, for format version 1. For input that is valid Unicode it's the same on the
JVM, on Node, and from the native binary.

The document is one line of UTF-8, followed by one line break. Non-ASCII characters aren't
escaped.

`requirement` is the text as the checker read it, which isn't always the file's text: a
plain-text line has the whitespace around it removed; a Markdown requirement has its markup
removed, entities decoded, line breaks turned into spaces, and inline code written as a backtick
span; an OpenSpec requirement's text is used as it is, so a line break in it is written as `\n`.

## The document

```json
{
  "version": 1,
  "checker": "0.2.0",
  "checked": 3,
  "findings": [
    {
      "location": { "type": "line", "file": "reqs.txt", "line": 2 },
      "requirement": "The scheduler should list upcoming events.",
      "kind": "weak-modal",
      "message": "Use shall instead of \"should\".",
      "span": { "start": 14, "end": 20 },
      "fix": "The scheduler shall list upcoming events."
    },
    {
      "location": { "type": "line", "file": "reqs.txt", "line": 3 },
      "requirement": "When an order arrives the checkout service shall send a receipt.",
      "kind": "missing-comma",
      "message": "Put a comma after the When clause.",
      "span": { "start": 21, "end": 21 },
      "fix": "When an order arrives, the checkout service shall send a receipt."
    }
  ]
}
```

That's `ears-check --output json reqs.txt` for this `reqs.txt` (exit status 1), spread over
lines here for reading:

```text
The scheduler shall list upcoming events.
The scheduler should list upcoming events.
When an order arrives the checkout service shall send a receipt.
```

| Field | Type | What it is |
|---|---|---|
| `version` | number | The format version: `1`. See [Stability](#stability). |
| `checker` | string | The ears-checker release that wrote the document, such as `0.2.0`; a build between releases gives the next version as a snapshot, such as `0.2.0-SNAPSHOT`. Findings can differ between releases within a format version. |
| `checked` | number | How many requirements were checked, in every input together. |
| `findings` | array | One object per finding, in input order: the inputs in command-line order, and each input's requirements in the order they appear. A requirement with several problems has one finding for each, ordered by where their spans start. Empty when every requirement passes. |

## A finding

| Field | Type | What it is |
|---|---|---|
| `location` | object | Where the requirement is: one of the [location types](#locations). |
| `requirement` | string | The requirement's text, as it was checked. |
| `kind` | string | The diagnostic's [kind](#kinds), such as `weak-modal`. |
| `message` | string | What's wrong and what to do, for people. Its wording can change in any release. |
| `span` | object | The part of `requirement` the diagnostic is about. See [Spans](#spans). |
| `fix` | string or `null` | The requirement with the problem fixed, when the checker can fix it; otherwise `null`. A fix corrects only this finding's problem. |

## Locations

A location's `type` says which fields it has.

### `line`: plain text, Markdown, and standard input

```json
{ "type": "line", "file": "reqs.txt", "line": 2 }
```

| Field | Type | What it is |
|---|---|---|
| `file` | string | The file name as given on the command line, or `<stdin>` for standard input. |
| `line` | number | The line the requirement starts on, counting from 1. A Markdown requirement can continue onto later lines. |

There's no column: in plain text it would only be the indentation, and in Markdown escapes and
entities make positions in the text differ from positions in the file.

### `change`: a requirement in an OpenSpec change

From `openspec show <change> --json`, which the checker reads for each added and modified
requirement:

```json
{ "type": "change", "change": "add-reminders", "capability": "reminders", "requirement": "Reminder sent" }
```

| Field | Type | What it is |
|---|---|---|
| `change` | string | The change's id. |
| `capability` | string | The capability (spec) the requirement belongs to. |
| `requirement` | string | The requirement's name, its `### Requirement:` heading. |

The whole document for that change, whose requirement is "The scheduler sends a notice."
(under the `openspec` profile, which OpenSpec input uses unless `--profile` says otherwise):

```json
{
  "version": 1,
  "checker": "0.2.0",
  "checked": 1,
  "findings": [
    {
      "location": { "type": "change", "change": "add-reminders", "capability": "reminders", "requirement": "Reminder sent" },
      "requirement": "The scheduler sends a notice.",
      "kind": "missing-modal",
      "message": "State the response with SHALL.",
      "span": { "start": 0, "end": 28 },
      "fix": null
    }
  ]
}
```

### `spec`: a requirement in an OpenSpec spec

From `openspec show <spec> --type spec --json`:

```json
{ "type": "spec", "spec": "reminders", "requirement": "Upcoming" }
```

| Field | Type | What it is |
|---|---|---|
| `spec` | string | The spec's id. |
| `requirement` | string | The requirement's name. |

The whole document for that spec, whose requirement is "The scheduler lists upcoming events.":

```json
{
  "version": 1,
  "checker": "0.2.0",
  "checked": 1,
  "findings": [
    {
      "location": { "type": "spec", "spec": "reminders", "requirement": "Upcoming" },
      "requirement": "The scheduler lists upcoming events.",
      "kind": "missing-modal",
      "message": "State the response with SHALL.",
      "span": { "start": 0, "end": 35 },
      "fix": null
    }
  ]
}
```

## Spans

`span` is `{ "start": <number>, "end": <number> }`, a half-open range `[start, end)` in the
finding's `requirement` (not in the file), counted in **UTF-16 code units**, as JavaScript and
Java index strings. In "The scheduler should list upcoming events." the `weak-modal` span is 14
to 20, the word "should". Any span can be empty (`start` equals `end`): for a missing comma or
period, for example, it's the place one belongs.

A character outside the Basic Multilingual Plane, such as an emoji, counts as two code units: in
"The 📅 scheduler should list upcoming events." the span is 17 to 23. Languages that index
strings by code point (Python, Rust's `chars`) must convert. In Python:

```python
units = requirement.encode("utf-16-le")
text = units[2 * start : 2 * end].decode("utf-16-le")
```

## Kinds

| Kind | The problem | Fix |
|---|---|---|
| `missing-modal` | No `shall` in any case, and no weak modal in its place: the response isn't stated. | no |
| `wrong-case` | The modal keyword in the wrong case for the profile. | yes |
| `weak-modal` | `must`, `should`, `may`, `will`, `can`, `could`, `might`, or `would` in place of `shall`. | yes |
| `empty-response` | Nothing after the modal keyword. | no |
| `multiple-responses` | More than one response in one requirement. | no |
| `multiple-sentences` | More than one sentence. | no |
| `missing-period` | No period at the end. | yes |
| `missing-comma` | No comma after a Where, While, When, or If clause. | yes |
| `missing-then` | An If clause not followed by `then`. | yes |
| `missing-article` | The system isn't named with `the` (no fix when it starts with a quantifier such as "all" or "each"). | sometimes |
| `clause-order` | Clauses out of the order Where, While, then When or If. | sometimes |
| `repeated-clause` | The same kind of clause twice. | no |
| `conflicting-triggers` | Both a When clause and an If clause. | no |
| `unrecognized-structure` | The sentence fits no EARS template. | no |

Whether a finding has a fix is in its `fix` field; the last column only says what to expect.

## Stability

While the version is 1:

- No field is removed or renamed, and none changes its type or meaning. Each change of that sort
  comes with a new `version`, and a consumer should refuse a version it doesn't know.
- A new location `type` also comes with a new version.
- These can change without a new version, so a consumer must allow for them:
  - new fields, in the document, a finding, or a location (ignore fields you don't know);
  - new kinds (treat an unknown `kind` as a finding like any other);
  - the wording of `message`, and which findings and fixes the checker gives for a requirement,
    as the classifier improves;
  - the order of fields within an object (read fields by name).
  - how a requirement's text is taken from its input (and so `requirement`, `span`, and
    `checked`), and how much of `requirement` a kind's span covers. A span still always indexes
    the same finding's `requirement`; the CHANGELOG says when either changes, and `checker`
    says which release wrote a document.

ears-checker is at 0.x, so its other interfaces can change in any minor release; this document's
version is how JSON output says it changed.

## When there's no document

- **An input can't be read or parsed** (a missing file, invalid UTF-8, JSON that isn't
  OpenSpec's, or Markdown given to the native binary with no cmark installed): exit status 2, a message on standard error, and nothing on standard output, even
  if other inputs were fine.
- **Invalid arguments**: exit status 2, the reason and the usage text on standard error.
- **`--help`** (as the first argument): the usage text, not JSON, on standard output, with exit
  status 0.

Otherwise the exit status is 0 when `findings` is empty and 1 when it isn't. Empty input, or input
with no requirements, gives `{"version":1,"checker":"0.2.0","checked":0,"findings":[]}` and status 0.
