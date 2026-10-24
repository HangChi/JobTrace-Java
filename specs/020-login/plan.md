# Implementation Plan: Compatible Account Login

**Branch**: `codex/020-login` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/020-login/spec.md`

## Summary

Add anonymous `POST /api/auth/login` with the old origin, rate-limit and optional challenge sequence. Resolve username or verified recovery email, verify the existing scrypt credential, reject disabled users without disclosing account state, create a seven-day legacy-shaped session, and issue a signed Better Auth session cookie. Reuse the 019 challenge adapter behind a shared auth port. The old service keeps production routing and protected API authentication.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1

**Primary Dependencies**: Spring Web/Security/JDBC, existing scrypt adapter, JDK HMAC/SecureRandom

**Storage**: Existing PostgreSQL users, accounts, sessions, auth_rate_limits; no schema migration

**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, JaCoCo, fixed cross-runtime cookie vector

**Target Platform**: Existing Spring Boot modular monolith

**Project Type**: Backend web-service migration slice

**Performance Goals**: Successful login p95 at most one second, excluding configured external challenge latency

**Constraints**: No trust in public identity headers or browser cookies for protected Java APIs; no CI/CD changes

**Scale/Scope**: One public command route plus credential/session persistence and cookie compatibility

## Constitution Check

- Maintainability: share the existing challenge verifier; isolate credential lookup, use case, and cookie encoding.
- Testing: validation, denial uniformity, PostgreSQL persistence, cookie signature and attributes, routing/security, latency; at least 80% line and branch coverage for changed production classes.
- UX/accessibility: no frontend changes; preserve public error copy and safe redirect behavior.
- Performance: representative successful login p95 <= 1 second; bounded outcome metrics without credentials, tokens or identifiers.
- Rollback: legacy remains production route owner; return only this POST to legacy if activated later.

## Project Structure

```text
src/main/java/com/jobtrace/identityaccess/
├── application/       # validated login and preflight ports
├── domain/            # generic credential denial and field validation
├── infrastructure/    # credential lookup, session insert, shared challenge, cookie signing
└── web/               # exact anonymous route, response cookie, no-store and metrics
src/main/java/com/jobtrace/shared/security/ # exact public POST allowance
src/test/java/com/jobtrace/identityaccess/  # unit, contract, security, DB, cookie and p95 tests
specs/020-login/                    # decisions, contracts and evidence
```

**Structure Decision**: Extend the existing identity-access bounded context. Java-issued sessions remain consumable by legacy but do not change Java's signed-bridge requirement for other routes.

## Complexity Tracking

No constitution exceptions. The cookie codec is separate because cross-runtime signing and browser attributes must be verified independently from credential logic.
