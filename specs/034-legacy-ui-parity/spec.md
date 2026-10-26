# Feature Specification: Legacy UI Parity Without Ingestion

**Status**: Implemented locally

## Scope

Close user-facing gaps found by comparing the local legacy JobTrace checkout with the Java/React application. Job-market collection, discovery, scheduling, synchronization and source administration are excluded.

## Requirements

- Closed market listings must not offer application or external apply actions; users can expand all available positions and locations on open listings.
- Import previews must permit inspection and selection of rows after the first 100, including duplicate candidates.
- Application lists must allow owner-scoped bulk deletion and all, filtered or selected export using existing Java APIs. Selection must respect the 100-ID server limit.
- Private interview editing must retain legacy Markdown content, show a safe preview, edit persisted interview metadata, ratings and action items, save private drafts after a quiet interval, and export one or several selected reviews.
- Profile settings must allow restoring the default avatar. The profile's interview export link must lead to review selection rather than an invalid download request.

## Verification

Frontend behavior is covered with component tests. `npm run lint`, `npm test`, `npm run build` and `npm run test:coverage` are required. No backend production code or legacy repository files change.
