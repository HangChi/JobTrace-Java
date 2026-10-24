# Implementation Plan: Interview Reviews and Public Sharing

**Branch**: `codex/024-interview-reviews` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Extend the Java private interview read slice with owner-scoped commands, public read projections and engagement. Preserve legacy PostgreSQL functions, JSON behavior and current production routing.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1
**Primary Dependencies**: Spring Web, Security, JDBC, Jackson; no new dependencies
**Storage**: Existing PostgreSQL review, question, action, like, comment and user tables/functions
**Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL, JaCoCo
**Target Platform**: Existing Spring Boot modular monolith
**Project Type**: JSON web service
**Performance Goals**: reads <= 500 ms p95, writes <= 1 s p95 under documented local fixture load
**Constraints**: no schema, frontend, CI/CD, deployment or production routing changes
**Scale/Scope**: private CRUD, public feed/detail, like/comment operations

## Constitution Check

- Maintainability: web validates requests; application use cases own behavior; JDBC adapters contain SQL and SQLSTATE mapping. No new service/dependency.
- Testing: unit/contract plus real PostgreSQL tests; changed-code line/branch coverage >= 80%.
- UX/accessibility: no new UI; preserve JSON/error wording and states for existing accessible clients.
- Performance: bounded pages/comments, database-side filtering/counts, representative p95 checks.
- Rollback: Java routes remain inactive in production until cutover; no schema rollback needed.

## Project Structure

```text
src/main/java/com/jobtrace/interviews/{domain,application,infrastructure,web}/
src/test/java/com/jobtrace/interviews/
src/test/resources/postgres/
specs/024-interview-reviews/
```

## Sequencing

Private commands first, reusing `InterviewReadQuery` for saved detail. Then implement a safe public projection and engagement functions. Verify contracts, isolation, coverage and latency before marking complete. All research and implementation are done without subagents.
