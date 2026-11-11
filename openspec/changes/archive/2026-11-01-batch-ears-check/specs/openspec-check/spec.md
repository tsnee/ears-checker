# Spec Delta

## ADDED Requirements

### Requirement: Single EARS checker run

When the OpenSpec check runs, the OpenSpec check SHALL start the EARS checker once for all active changes and specs.

#### Scenario: Two changes and four specs

- **WHEN** the check runs with two active changes and four specs
- **THEN** it starts the EARS checker once, and the report covers requirements from all six
