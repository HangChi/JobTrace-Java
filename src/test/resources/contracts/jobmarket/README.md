# Job-market read compatibility oracle

Feature 008 reproduces the authenticated reads implemented by the legacy
`campaign-service.ts`, `contracts.ts`, `postgres-campaign-query.ts`,
`apply-target.ts`, and the company-read-model migration. Fixtures are synthetic.

- `GET /api/job-market/campaigns` defaults to page 1 and limit 20. Text filters
  are trimmed, blank means absent, and `q`, `company`, and `location` are limited
  to 100 characters. `favorite=true` filters favorites and enables the
  include-closed projection; `favorite=false` behaves like omission.
- Default reads use `include_closed=false`. Only `status=closed` or
  `favorite=true` uses `include_closed=true`.
- Companies sort by published time descending/nulls last, last-confirmed time
  descending/nulls last, then company UUID. List positions are capped at 50
  while `positionCount` remains complete.
- Detail resolves any campaign ID to its company, returns that company's current
  representative summary and all non-closed jobs with an active source. Jobs
  sort by publication descending/nulls last, title, then UUID. Sources prefer
  official and then most recently seen records.
- Favorites and tracked-application IDs are owner-bound. No caller parameter or
  public identity header selects an owner.
- Only canonical HTTPS targets are actionable. Stale jobs use `该岗位已失效`;
  open jobs without a safe target use `来源未提供安全的官方投递地址`.
- Java intentionally strengthens privacy by returning `private, no-store`
  instead of reproducing the legacy 30-second owner-keyed server cache.

The legacy service remains projection maintainer, synchronization runner,
favorite/tracking writer, schema owner, session authority, and traffic owner.
