# Application read model migration slice

| Field | Value |
| --- | --- |
| Slice | `application-read-model` (feature 004) |
| Capability | `GET /api/applications`, `GET /api/applications/{id}` |
| State | Code verification only; production activation unscheduled |
| Session and write owner | Existing JobTrace service |
| Schema owner | Existing JobTrace service; Flyway disabled in Java |
| Java role | Read-only implementation using the signed identity principal |

The Java module reads the existing PostgreSQL `applications`,
`application_stage_occurrences`, and `application_events` tables. It does not
create or mutate any business row. All SQL binds the authenticated owner. The
list implements the existing search, repeated filters, sorting, offset page,
and continuation cursor behavior. The detail read returns core fields, stage
occurrences, and the complete event history. The interview-enriched dialog
and every application mutation remain in the existing service.

## Contract and security evidence

The checked-in synthetic legacy fixtures cover an empty list, a representative
page, and a complete detail. Integration tests compare their JSON structures,
including array order, against the Java responses. Additional PostgreSQL tests
cover cross-owner counts and identifiers, combined filters, date boundaries,
all four sorts in both directions, cursor traversal, stage and event order, and
the same 404 outcome for absent and cross-owner details.

The feature 003 signed identity bridge is extended to both application routes.
Its request binding, signature, expiry, and single-use replay guard remain the
source of the Java principal. Tests reject public owner headers, forged,
expired, path-mismatched, and replayed assertions. Read metrics have only
operation and bounded outcome labels. Query text, identities, and application
identifiers are not metric labels.

## Verification and release boundary

Run `./mvnw verify` with Docker available. This includes the PostgreSQL
fixtures, contract and security tests, architecture rules, Checkstyle,
coverage, performance budget, embedded frontend build, and Java artifact.
The feature-specific OpenAPI contract is under
`specs/004-application-read-model/contracts/openapi.yaml`; the two routes
and response schemas are also in the repository API baseline.

Java deployment, traffic routing, canary observation, and a production
rollback exercise are not scheduled for this feature. The current production
read and all writes continue through the existing JobTrace service. If a
later release chooses to activate Java, it needs a separate reviewed plan
covering private ingress, issuer routing, observation, and rollback.
