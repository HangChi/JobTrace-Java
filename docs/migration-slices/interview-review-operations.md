# Interview Review Operations (Feature 024)

Java implements the existing authenticated private review create, edit and delete routes,
public feed/detail routes, and public like/comment routes. The old service remains the
production route owner; this slice does not switch traffic, change the frontend, or alter
the shared PostgreSQL schema.

Private writes derive the owner from a verified browser session or signed bridge, never
from a request header. Creation and editing call the existing owner-aware PostgreSQL
functions so stage chronology, completed-review publication requirements, optimistic
versions, autosave behavior and nested-content atomicity stay with the current sole
database writer. Delete includes the owner predicate. Missing and foreign identifiers
share a not-found response; stale edits return conflict.

The public reader selects an explicit safe field allowlist and only reviews that are
completed, public and currently published. Anonymous mode omits author identity.
Question text is scrubbed with the legacy Markdown privacy rules. Private ratings,
interviewer notes, action items, owner/application identifiers and private application
metadata do not enter public response models. Latest/hot pages use bounded cursor
pagination; detail records unique views through the existing database function.
Likes and comments use the existing transactional PostgreSQL functions, which check
current availability and maintain counters. A review made private immediately drops
out of feed/detail and rejects new engagement, including access through an old URL.

Local PostgreSQL/HTTP tests cover validation, owner isolation, conflicts, safe public
projection, pagination, publication state, view count, likes, comments and error
paths. The performance fixture and measured p95 are recorded in
[`specs/024-interview-reviews/quickstart.md`](../../specs/024-interview-reviews/quickstart.md).
Response bodies are no-store; unexpected storage errors remain failures rather than
being acknowledged as successful writes. Use request IDs and aggregate request
status/latency as health signals, without logging review or comment content.

Rollback is routing affected traffic back to the old service (or leaving Java routes
inactive). The old service and shared database remain authoritative, so no data or
schema rollback is needed. Production activation, monitoring and later retirement of
old routes are separate migration decisions outside this stage.
