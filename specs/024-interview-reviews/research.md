# Research Decisions

- Reuse `create_interview_review_for_owner` and `update_interview_review_for_owner`, and delete with `owner_id`. These already enforce chronology, conflicts, nested-content atomicity, publication and autosave; duplicating rules in Java risks drift.
- Keep public read separate from private detail. An explicit field allowlist prevents future leakage of ratings, action items, interviewer notes, owner and application IDs.
- Reuse `record_public_interview_view`, `toggle_public_interview_like` and `add_public_interview_comment` for transactional availability checks and counters; Java-side multi-query updates would race.
- Preserve verified identity for public routes because legacy routes require sign-in. Anonymous browse would be a product change.
- Use existing Testcontainers setup and no migration/ORM ownership, as required by the Java migration rules.
