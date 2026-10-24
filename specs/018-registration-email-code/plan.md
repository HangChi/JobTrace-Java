# Implementation Plan: Request Registration Email Code

**Branch**: `codex/018-registration-email-code` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/018-registration-email-code/spec.md`

## Summary

Add only the anonymous `POST /api/auth/email-code` issuance command. Reuse stage 017's secure code generator, delivery adapter, and PostgreSQL rate-limit rules; use a registration-purpose HMAC and null user binding. Exempt only this route from Spring's CSRF token requirement while preserving the old same-origin check. The old service retains registration and production routing.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1, Spring MVC/Security, Spring JDBC, existing Java HTTP delivery adapter

**Storage**: Existing PostgreSQL users, email_verification_codes, auth_rate_limits and consume_auth_rate_limit

**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers PostgreSQL, OpenAPI parser

**Target Platform**: Existing Spring Boot modular monolith

**Project Type**: Anonymous web API migration slice

**Performance Goals**: Issuance/persistence p95 <= 1 second excluding remote delivery latency

**Constraints**: Exact anonymous POST only, same-origin browser boundary, no schema changes, no plaintext code in storage/response, shared secret and delivery contract, 80% changed-code line and branch coverage

**Scale/Scope**: One registration-code request route; no account creation, redemption, frontend, routing, deployment, or CI/CD

## Constitution Check

- Maintainability: reuse stage 017 generator/delivery/limits; keep registration-specific persistence separate where null owner semantics differ.
- Testing: unit, database, HTTP, origin/security, compatibility, and performance tests.
- UX: preserve accepted message and field-specific conflict; no frontend change.
- Performance: measure issuance with stubbed external delivery and enforce ten-second delivery timeout from stage 017.
- Security: reject explicit cross-site/mismatched Origin, keep exact CSRF exception and anonymous authorization, use purpose-separated HMAC and safe diagnostics.

All gates pass before and after design.

## Project Structure

```text
src/main/java/com/jobtrace/identityaccess/
├── application/       # registration-code use case and store/hash ports
├── domain/            # safe errors
├── infrastructure/    # registration persistence and shared rate limits
└── web/               # anonymous route, origin guard, bounded metrics
src/main/java/com/jobtrace/shared/security/SecurityConfig.java
src/test/java/com/jobtrace/identityaccess/
specs/018-registration-email-code/
docs/migration-slices/
```

**Structure Decision**: Extend the existing identity-access module; modify only the exact public-route rule in shared security. No new dependency or deployment unit.
