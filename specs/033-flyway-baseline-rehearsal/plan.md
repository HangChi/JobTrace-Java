# Plan

1. Pin the clean local legacy revision and migration manifest.
2. Replay the chain and rehearse backup/restore in disposable PostgreSQL 17 containers.
3. Exercise Flyway's explicit baseline API at the head without importing legacy SQL.
4. Add a repeatable Testcontainers adoption check and run the Java quality gate.
5. Record the isolation boundary and keep production activation disabled.
