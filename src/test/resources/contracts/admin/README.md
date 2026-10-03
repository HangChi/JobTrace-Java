# Administration legacy read contract

Source routes are `GET /api/admin/summary`, `GET /api/admin/users`,
`GET /api/admin/users/{id}`, and `GET /api/admin/audit-events` in the current JobTrace service.

- Every route requires a non-disabled administrator.
- Lists default to page 1 and limit 50, allow limit 1..100, sort by timestamp then ID descending,
  and clamp a non-empty out-of-range page to the last page.
- User content pages are independent, fixed at 10, and also clamp.
- Optional text is trimmed/omitted and bounded to 100 characters. Dates are inclusive Shanghai
  calendar dates and ordered ranges are required.
- User display email prefers recovery email. Display username prefers display username, username,
  then the internal email prefix.
- Audit snapshots survive deleted actors/targets. Recent detail history is limited to 10.
- Summary counts and activity are independent availability sections; activity is a gap-free 30-day
  Shanghai trend and active means a non-disabled user with at least one session in the window.
- Responses are intentionally hardened with `private, no-store` in Java.
- PATCH access changes, sessions, market administration, sync, collectors, schema changes, frontend,
  deployment, traffic routing, and any SQL write are excluded.
