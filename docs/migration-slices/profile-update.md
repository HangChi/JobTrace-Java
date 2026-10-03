# Feature 013: account profile update

## Boundary

Feature 013 adds only `PATCH /api/profile`. A verified account owner can update the display name and store or clear an already-hosted avatar URL. The stable response also includes unchanged recovery-email fields.

Avatar upload and cloud storage, email binding, passwords, registration, login/logout, session issuance and refresh, frontend routing, deployment, production traffic, and CI/CD remain with the existing service.

## Authorization and validation

The exact PATCH path uses signed identity bridge v1 bound to method, path, and request ID. The verified subject is the only owner selector. Display names are trimmed and limited to 100 characters. An image may be an absolute URL, empty, null, or omitted; the last three forms store null. Unknown properties are ignored for compatibility.

## Persistence contract

The adapter performs one parameterized update against the unchanged `users` table:

- `id` must equal the verified bridge subject;
- only `display_name`, `image`, and `updated_at` change;
- recovery email and verification state are returned without mutation;
- role, disabled state, access version, credentials, and sessions are untouched.

## Operations

Successful and failed responses use `Cache-Control: private, no-store`. Metrics contain only bounded outcome and latency labels. Account identifiers, display names, emails, image URLs, assertions, request bodies, and SQL details are prohibited from diagnostics. The representative p95 budget is one second.

## Activation and rollback

Activation is unscheduled. The legacy issuer must provide a PATCH-bound assertion before traffic can be routed to Java. Rollback routes PATCH back to the existing service; no data or schema rollback is required.
