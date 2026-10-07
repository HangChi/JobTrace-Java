# Data Model: Frontend Migration

## Client session

`SessionState` is loading, anonymous, authenticated or unavailable. Authenticated state contains `user.id`, `displayName`, optional `image`, and `role`; the cookie remains HttpOnly and never enters JavaScript. A 401 during protected work transitions to anonymous and retains a safe return path.

## Route state

Path selects page group. Search parameters hold shareable filters, pagination and safe return destination. Back/forward restores the URL and re-renders the matching view. Unsupported or unfinished 027 actions render an explanatory unavailable state.

## Remote resources

Applications, reminders, analytics, interviews, import batches, profile and admin data are fetched from Java. Each resource view has loading, ready, empty, validation/error and retry states. Mutations disable repeat submission until resolved and update or re-fetch the affected resource on success.

## Form state

Controlled values survive validation errors. Field errors map from Java problem responses by field name. Unrecognized problem shapes yield a safe generic message with a retry path; request IDs may be displayed for support but not logged with sensitive input.
