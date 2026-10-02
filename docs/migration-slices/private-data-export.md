# Private data export read slice (007)

Java implements two opt-in, read-only compatibility downloads: `GET /api/exports/applications`
and `GET /api/exports/interviews`. The existing Next.js service remains the sole importer,
application/review writer, session owner, database-schema owner and production traffic owner.
There is no Java deployment or routing switch planned for this slice.

The application download supports the current all/filtered/selected scopes, CSV and XLSX,
owner-bound selection, current column/label order and formula-inert cells. The interview
download returns one Markdown file or an ordered ZIP of multiple owned reviews. Both routes
require a short-lived, one-use, method/path/request-ID-bound signed identity bridge assertion;
the browser's session cannot be presented directly to Java.

The existing application route did not explicitly set a cache policy. Java intentionally
sets `Cache-Control: private, no-store` for successful downloads, matching the existing
interview download's privacy posture. Neither route persists an export artifact. Metrics
contain only a fixed operation and outcome label; they never include owner IDs, selections,
filenames or exported content. Database reads are parameterized and ownership is checked
at the application and review roots before any child content is included.

Verification uses only synthetic two-owner PostgreSQL fixtures. The source model exists under
`src/test/resources/postgres/`; no production DDL or Flyway migration was added. The slice's
contract and validation status are in `specs/007-private-data-export/`. Production activation,
identity-issuer changes and rollback rehearsal require a separate release decision.
