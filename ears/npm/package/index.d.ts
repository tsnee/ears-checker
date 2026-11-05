/** Which letter case the modal keyword must have: `shall` (classic) or `SHALL` (OpenSpec). */
export type Profile = "classic" | "openspec";

/** Options for `classify`. Every field is optional. */
export interface Options {
  /** The modal keyword's case; `"classic"` when absent. */
  readonly profile?: Profile;
}

/** A half-open range `[start, end)` of UTF-16 code units: `sentence.slice(start, end)`. */
export interface Span {
  readonly start: number;
  readonly end: number;
}

/** One clause of a requirement, as written in the sentence. */
export interface Clause {
  readonly text: string;
  readonly span: Span;
}

/** The clause that starts the response: an event (When) or unwanted behavior (If). */
export interface Trigger {
  readonly kind: "event" | "unwanted";
  readonly clause: Clause;
}

/** A requirement that matches an EARS template. An absent clause is `null`. */
export interface Requirement {
  readonly feature: Clause | null;
  readonly precondition: Clause | null;
  readonly trigger: Trigger | null;
  readonly system: Clause;
  readonly response: Clause;
}

/** The EARS template a requirement matches. */
export type Template =
  | "ubiquitous"
  | "event-driven"
  | "state-driven"
  | "unwanted-behavior"
  | "optional-feature"
  | "complex";

/** The kind of problem a diagnostic reports. */
export type DiagnosticKind =
  | "missing-modal"
  | "wrong-case"
  | "multiple-responses"
  | "weak-modal"
  | "empty-response"
  | "clause-order"
  | "repeated-clause"
  | "conflicting-triggers"
  | "missing-then"
  | "missing-comma"
  | "missing-article"
  | "missing-period"
  | "multiple-sentences"
  | "unrecognized-structure";

/** Replace the text in `span` with `replacement`. */
export interface TextEdit {
  readonly span: Span;
  readonly replacement: string;
}

/** Edits that correct one problem, which don't overlap, and the sentence with them applied. */
export interface Fix {
  readonly edits: readonly TextEdit[];
  readonly fixed: string;
}

/** One problem with a sentence, and its fix when there is one. */
export interface Diagnostic {
  readonly kind: DiagnosticKind;
  readonly message: string;
  readonly span: Span;
  readonly fix: Fix | null;
}

/** A sentence that matches an EARS template. */
export interface Match {
  readonly ok: true;
  readonly template: Template;
  readonly requirement: Requirement;
}

/** A sentence that doesn't match: every problem found. */
export interface Mismatch {
  readonly ok: false;
  readonly diagnostics: readonly Diagnostic[];
}

/** The result of `classify`; check `ok` to tell which. */
export type Result = Match | Mismatch;

/**
 * Classifies one requirement sentence against the EARS templates.
 *
 * @throws {TypeError} if `sentence` isn't a string, `options` isn't an object, or
 *   `options.profile` isn't `"classic"` or `"openspec"`. A sentence that doesn't fit EARS is
 *   never an error: it's a `Mismatch`.
 */
export function classify(sentence: string, options?: Options): Result;
