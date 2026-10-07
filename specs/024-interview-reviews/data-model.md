# Data Model

| Entity | Key fields | Invariants |
| --- | --- | --- |
| Interview review | id, owner, application, stage, date, status, visibility, author mode, published date, version, autosave revision | Owner-scoped; public only when completed and published; edits versioned. |
| Question | id, review, order, category, question, answers, self-rating | Nested update atomic; self-rating private. |
| Action item | id, review, order, content, completed | Private, ordered, atomic update. |
| Like | review, viewer | At most one active like per viewer; toggle updates count. |
| Comment | id, review, author, content, created date | 1-1000 trimmed characters; newest-first listing. |

Existing database functions own stage/date, publication, conflict and autosave transitions. Java validates input and maps SQLSTATE outcomes.
