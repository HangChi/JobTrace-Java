# Implementation Plan: Request Email Binding Code

**Branch**: `codex/017-email-verification-code` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/017-email-binding-code/spec.md`

## Summary

Implement the exact authenticated `POST /api/profile/email/code` command. Validate the address, require an active owner session, apply legacy rate limits, check email ownership, generate a random six-digit code, store only its legacy-compatible HMAC, invalidate prior codes, and send through the existing HTTP delivery contract. Routing remains with the old service.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1, Spring MVC, Spring JDBC, JDK HTTP client

**Storage**: Existing PostgreSQL `users`, `sessions`, `email_verification_codes`, `auth_rate_limits` and `consume_auth_rate_limit`

**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers PostgreSQL, local HTTP test server

**Target Platform**: Existing Spring Boot modular monolith

**Project Type**: Authenticated web API migration slice

**Performance Goals**: Code generation/persistence p95 at most one second, excluding remote delivery latency

**Constraints**: No schema changes; no plaintext code in storage or response; existing 016 HMAC compatibility; safe delivery failure; 80% changed-code line and branch coverage

**Scale/Scope**: One POST route and one mail template; no registration, frontend, routing, deployment, or CI/CD

## Constitution Check

- Maintainability: existing identity module and application ports; no new service or dependency.
- Testing: unit, database, HTTP, security, delivery-contract and performance tests.
- UX: preserve status, message, and safe problem codes; no frontend change.
- Performance: measure issuance excluding external service latency and bound delivery timeout.
- Security: exact signed assertion, owner session, HMAC code storage, fixed metric labels, no plaintext diagnostics.

All gates pass before and after design.

## Project Structure

```text
src/main/java/com/jobtrace/identityaccess/
├── application/        # request use case and persistence/delivery ports
├── domain/             # result and safe failures
├── infrastructure/     # PostgreSQL, secure code generator, HTTP delivery
└── web/                # exact POST controller and bounded metrics
src/test/java/com/jobtrace/identityaccess/  # unit, database, HTTP and quality tests
specs/017-email-binding-code/              # specification and contracts
docs/migration-slices/                     # routing and rollback note
```

**Structure Decision**: Extend the current identity-access module with one vertical slice. No JPA mapping or new deployment unit.
