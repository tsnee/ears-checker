## ADDED Requirements

### Requirement: No source map in the CLI package

The CLI npm package SHALL contain no source map.

#### Scenario: Packed CLI package

- **WHEN** a developer packs the CLI npm package
- **THEN** no file in it ends in `.map`, and its `main.js` has no `sourceMappingURL` comment

### Requirement: CLI code size

The CLI npm package SHALL ship its code in a `main.js` of at most 2,100,000 bytes.

#### Scenario: Development build

- **WHEN** a developer builds the CLI npm package
- **THEN** its `main.js` is at most 2,100,000 bytes
