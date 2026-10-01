# Analytics summary legacy fixtures

These synthetic fixtures capture the observable JSON contract of the legacy
`GET /api/analytics/summary` implementation. They contain no production data.

- Source repository revision: `c9e00dd685344c2fc054212d4eb11e5d558a0f7a`
- Capture date: `2026-10-01`
- Business date for the representative case: `2026-09-30` in `Asia/Shanghai`
- Route SHA-256: `9a030863cd6c94e9540c41aaee97627dedf02fe5829817e122d9c57c14bcddbc`
- Contract SHA-256: `be71f04ec783a495d9c59e1200923f790bf7b511e87abb92062eadd26bf0a56e`
- Query SHA-256: `802704f3ca72934a2fe23606ad4b617d3b690565f67a0e58024d054a04a9aff8`

The source working tree had unrelated and uncommitted changes at capture time.
The hashes above pin the three behavioral source files independently of that
working tree state. `empty.legacy.json` records an owner with no applications;
`representative.legacy.json` records owner-scoped counts, stages, follow-ups,
and progress reminders using stable synthetic identifiers.
