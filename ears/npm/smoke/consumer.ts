// A TypeScript consumer of the installed package. scripts/check-npm-package type-checks it under
// Node and bundler module resolution, then runs it with Node. Any wrong answer throws, which
// exits Node with a non-zero status.
import { classify } from "@tsnee/ears-checker";
import type {
  Clause,
  Diagnostic,
  DiagnosticKind,
  Options,
  Profile,
  Result,
  Span,
  Template,
} from "@tsnee/ears-checker";

function expect(condition: boolean, what: string): void {
  if (!condition) throw new Error(`check-npm-package: ${what}`);
}

function textOf(sentence: string, span: Span): string {
  return sentence.slice(span.start, span.end);
}

const eventDriven =
  "When a user submits an order, the checkout service shall send a confirmation email.";
const matched: Result = classify(eventDriven);
if (matched.ok) {
  const template: Template = matched.template;
  const system: Clause = matched.requirement.system;
  expect(template === "event-driven", `template is ${template}`);
  expect(textOf(eventDriven, system.span) === "checkout service", "system clause span");
  expect(matched.requirement.trigger?.kind === "event", "event trigger");
  expect(matched.requirement.feature === null, "no feature");
  // Results are read-only. Each assignment has a value of the right type, so that only
  // `readonly` makes it an error.
  // @ts-expect-error: read-only field.
  matched.ok = true;
  // @ts-expect-error: read-only field.
  matched.template = "complex";
} else {
  throw new Error("check-npm-package: an event-driven requirement didn't match");
}

const profile: Profile = "openspec";
const options: Options = { profile };
const wrongCase: Result = classify("The scheduler shall list upcoming events.", options);
if (wrongCase.ok) {
  throw new Error("check-npm-package: a lowercase modal matched the OpenSpec profile");
} else {
  const diagnostic: Diagnostic | undefined = wrongCase.diagnostics[0];
  const kind: DiagnosticKind | undefined = diagnostic?.kind;
  expect(kind === "wrong-case", `kind is ${String(kind)}`);
  expect(
    diagnostic?.fix?.fixed === "The scheduler SHALL list upcoming events.",
    "fixed sentence",
  );
  // @ts-expect-error: read-only array.
  wrongCase.diagnostics.pop();
  // @ts-expect-error: read-only array.
  diagnostic?.fix?.edits.pop();
}

// Called as plain JavaScript: no options at all, and an extra argument, which is ignored.
const untyped = classify as (...args: unknown[]) => Result;
expect(untyped("The scheduler shall list upcoming events.").ok, "no options: classic profile");
expect(
  untyped("The scheduler SHALL list upcoming events.", { profile: "openspec" }, 42).ok,
  "an extra argument is ignored",
);

let thrown: unknown = undefined;
try {
  // Not a Profile: plain JavaScript callers can pass anything.
  classify("The scheduler shall list upcoming events.", { profile: "open-spec" as Profile });
} catch (error) {
  thrown = error;
}
expect(thrown instanceof TypeError, "an unknown profile throws a TypeError");
