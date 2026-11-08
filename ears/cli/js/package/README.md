# @tsnee/ears-checker-cli

The `ears-check` command: checks requirements in plain text, Markdown, and OpenSpec JSON against
the EARS templates (Easy Approach to Requirements Syntax), and says how to fix the ones that
don't fit. It's the command line from
[ears-checker](https://github.com/tsnee/ears-checker), compiled to JavaScript for Node 22 or
newer. For the classifier as a library, use `@tsnee/ears-checker`.

Install it with `npm install -g @tsnee/ears-checker-cli`, or from the tarball each
[GitHub release](https://github.com/tsnee/ears-checker/releases) attaches. Its options and
output may change in any 0.x release.

## Usage

```text
$ cat reqs.txt
When a customer submits an order the checkout service shall send a confirmation email.
The scheduler should list upcoming events.
While the cart is empty, the checkout service shall disable the pay button.
$ ears-check reqs.txt
reqs.txt:1: missing-comma: Put a comma after the When clause. Fix: "When a customer submits an order, the checkout service shall send a confirmation email."
reqs.txt:2: weak-modal: Use shall instead of "should". Fix: "The scheduler shall list upcoming events."
$ echo $?
1
```

`ears-check [--format <string>] [--profile <string>] [--output <string>] [--verbose] [<file>...]`
reads each file, or standard input when none is given:

- `--format`: `text` (one requirement per line), `markdown`, or `openspec` (JSON from
  `openspec show --json`). By default `.md` files are Markdown, `.json` files are OpenSpec,
  and everything else is text.
- `--profile`: `classic` (lowercase `shall`) or `openspec` (uppercase `SHALL`, which
  OpenSpec's validator requires). OpenSpec input defaults to `openspec`, other input to
  `classic`.
- `--output`: `text` (one line per finding) or `json` (one document, described in
  [JSON output](https://github.com/tsnee/ears-checker/blob/main/docs/json-output.md)).
- `--verbose`: log every step to standard error, not only warnings and errors.
- `--help`: print the usage and these options. For now it works only as the first argument;
  elsewhere it's an unexpected option (exit status 2).
- Inline code: in every format, text between backticks is quoted, as in Markdown; its periods,
  commas, and words don't count.
- Encoding: input is read as UTF-8 whatever the locale, and an input that isn't valid UTF-8 is
  an error giving the line and column of the first bad byte; a leading UTF-8 byte-order mark is
  ignored, and UTF-16 or UTF-32 input is named by its byte-order mark. Output is written as
  UTF-8.
- Exit status: 0 when every requirement passes, or for `--help` as the first argument; 1 when any
  finding is reported; 2 when the arguments are invalid or an input can't be read or parsed.

It gives the same output, messages, and exit status as the JVM build of `ears-check`. What it
checks, and its limits (in Markdown, only sentences containing `shall` are checked), are in the
[ears-checker README](https://github.com/tsnee/ears-checker#limits).

## License

CC0 1.0: no rights reserved.
