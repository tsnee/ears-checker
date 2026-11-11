# @tsnee/ears-checker

Checks requirement sentences against the EARS templates (Easy Approach to Requirements Syntax), and says how to fix the ones that don't fit. It's the classifier from [ears-checker](https://github.com/tsnee/ears-checker), compiled to JavaScript, with TypeScript declarations and no dependencies.

Install it from the tarball each [GitHub release](https://github.com/tsnee/ears-checker/releases) attaches (`npm install ./tsnee-ears-checker-<version>.tgz`); it isn't on npm yet. The API may change in any 0.x release.

## Usage

```ts
import { classify } from "@tsnee/ears-checker";

const result = classify(
  "When a user submits an order, the checkout service shall send a confirmation email.",
);
if (result.ok) {
  console.log(result.template); // "event-driven"
  console.log(result.requirement.system.text); // "checkout service"
} else {
  for (const diagnostic of result.diagnostics) {
    console.log(diagnostic.kind, diagnostic.message, diagnostic.fix?.fixed);
  }
}
```

`classify(sentence, options?)` takes one sentence and returns a `Result`:

- **`ok: true`:** the `template` it matches (`"ubiquitous"`, `"event-driven"`, `"state-driven"`, `"unwanted-behavior"`, `"optional-feature"`, or `"complex"`) and the `requirement`'s clauses: `feature`, `precondition`, `trigger`, `system`, and `response`. A clause the sentence doesn't have is `null`.
- **`ok: false`:** every `diagnostic`, with its `kind` (such as `"missing-modal"`), `message`, and `span`. A diagnostic that can be fixed has a `fix` with the `edits` and the `fixed` sentence; one that can't has `fix: null`.

A span's `start` and `end` count UTF-16 code units, so `sentence.slice(start, end)` is its text.

## Options

- **`profile`:** `"classic"` (the default) expects a lowercase `shall`; `"openspec"` expects `SHALL`, as OpenSpec's validator does.

```ts
classify("The scheduler SHALL list upcoming events.", { profile: "openspec" }).ok; // true
```

## Errors

`classify` throws a `TypeError` only for a caller's mistake: a sentence that isn't a string, options that aren't an object, or an unknown profile. A sentence that doesn't fit EARS is never an error; it's an `ok: false` result.

## License

CC0 1.0: no rights reserved.
