# Browser routes and Java API contracts

All API calls use the current origin and the signed HttpOnly browser cookie. The client never sends an owner or role header. JSON errors use ProblemDetail with `code`, `detail`, and optionally `fieldErrors`.

| Browser route | Java endpoint | Notes |
| --- | --- | --- |
| `/login` | `GET /api/auth/session`, `POST /api/auth/login` | Login body: `identifier`, `password`, optional internal `returnTo`; response: `redirectTarget`. |
| `/register` | `POST /api/auth/email-code`, `POST /api/auth/register` | Registration body: `username`, `email`, `verificationCode`, `password`, optional `displayName`. |
| `/forgot-password`, `/reset-password` | `POST /api/auth/request-password-reset`, `POST /api/auth/reset-password` | Reset body: `token`, `newPassword`; never expose token outside request. |
| All protected routes | `GET /api/auth/session`, `POST /api/auth/logout` | Session response has `session` and `user` (`id`, `displayName`, `image`, `role`). A 401 clears protected UI. |
| `/applications` | `/api/applications/**` | Applications, stage history and statuses. |
| `/reminders` | `/api/reminders/**` | Reminder commands and settings. |
| `/analytics` | `/api/analytics/**` | Summary, reports and progress. |
| `/import` | `/api/import/**`, `/api/export/**` | Preview then explicit confirmation. |
| `/interviews`, `/interviews/public` | `/api/interviews/**` | Private and public records, comments and likes. |
| `/profile` | `/api/profile/**` | Profile, credentials and account settings. |
| `/admin` | `/api/admin/**` | Role `ADMIN` only; 027 marketplace endpoints are incomplete on this branch. |

Unknown browser routes show a not-found state. Direct protected deep links retain only a same-origin path in `returnTo`. Any unfinished route must say it is unavailable rather than display a fake success state.
