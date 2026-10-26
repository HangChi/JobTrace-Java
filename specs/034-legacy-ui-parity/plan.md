# Implementation Plan

Reuse Java's existing owner-scoped bulk delete, export, interview update and profile update endpoints. Change React components in `frontend/src/features/`; keep job-market ingestion in the legacy service. Use safe React-rendered Markdown preview, debounced draft saves with server versions, and bounded selections. Add focused component regressions for each behavior. Run frontend quality gates and review the final diff for generated files or secrets.
