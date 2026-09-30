# Research: Java Migration Foundation

## Java runtime

**Decision**: Use Java 21 LTS.

**Rationale**: It is an installed, supported LTS runtime with a long compatibility horizon and avoids requiring contributors to adopt a newer runtime solely for this migration.

**Alternatives considered**: Java 25 LTS would offer a longer horizon but is not needed for the foundation and would add a local prerequisite; Java 17 is supported but shortens the useful baseline.

## Backend framework

**Decision**: Use Spring Boot 4.1.1 from the stable release advertised by Spring Initializr metadata on 2026-09-30.

**Rationale**: It provides the web, validation, security, health, JDBC, jOOQ, migration, and testing integration needed by the existing modular monolith.

**Alternatives considered**: Quarkus and Micronaut have strong runtime characteristics but would require more migration-specific conventions; a manually assembled servlet stack adds maintenance without user value.

## Database access

**Decision**: Combine jOOQ for type-safe SQL with Spring JDBC for direct stored-routine and specialized query access.

**Rationale**: The legacy schema contains PostgreSQL functions, JSONB, custom enums, optimistic locking, leases, and reporting queries. Preserving SQL semantics is safer than remapping them immediately to entities.

**Alternatives considered**: JPA/Hibernate was rejected for the first migration phase because it can obscure query shape and transaction behavior; raw JDBC alone was rejected because generated types and composable queries will reduce errors as coverage grows.

## Migration ownership

**Decision**: Include Flyway but disable it by default until a reviewed legacy baseline is approved.

**Rationale**: The existing repository has its own migration ledger and checksums. Automatic Flyway execution against an existing database before baselining could replay history or create false ownership.

**Alternatives considered**: Copying all legacy SQL into Flyway was rejected because it risks checksum drift and duplicate execution; leaving migration ownership permanently in the old repository would prevent independent operation.

## Frontend and production packaging

**Decision**: Retain React and TypeScript, replace Next.js with Vite, and embed the compiled output in the Spring Boot JAR.

**Rationale**: This preserves reusable UI logic and browser typing while removing Node.js from production. Development and CI retain Node.js as a build tool.

**Alternatives considered**: Thymeleaf/HTMX would eliminate Node.js completely but require a UI rewrite; Vaadin would create deeper framework coupling; keeping Next.js would retain the unwanted production Node.js runtime.

## Authentication transition

**Decision**: Migrate authentication last. Early protected slices will use a separately specified, short-lived, signed server-to-server identity bridge issued only after the legacy service validates its session.

**Rationale**: Better Auth currently owns password hashes, sessions, email verification, reset flows, rate limits, role changes, and session revocation. Moving it together with the first business slice would multiply risk.

**Alternatives considered**: Trusting forwarded identity headers was rejected as unsafe; reading Better Auth cookies directly in Java was rejected because it tightly couples the new service to undocumented session internals; forcing every user to reset credentials immediately was rejected as disruptive.

## Repository strategy

**Decision**: Use a new repository with a clean history and preserve the existing repository as the behavioral reference until migration completion.

**Rationale**: The selected architecture and build lifecycle are materially different, while contract fixtures and documentation can be copied deliberately with provenance.

**Alternatives considered**: Rewriting in-place would make rollback and release ownership ambiguous; preserving the entire source history would import large amounts of irrelevant Node.js server history.

