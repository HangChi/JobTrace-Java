# Signed identity bridge contract v1

## Transport

```http
GET /api/analytics/summary HTTP/1.1
Authorization: JobTraceBridge <compact-JWS>
x-request-id: <UUID>
Accept: application/json
```

Ingress prevents browsers and untrusted networks from reaching this internal target. Java still
requires the assertion. The token must not appear in URLs, cookies, responses, or logs. Maximum
encoded length is 4096 bytes.

## Protected surface

Version 1 authorizes exactly `GET /api/analytics/summary`. Any other method or path is invalid.

## Protected header

```json
{"alg":"HS256","kid":"2026-10-primary","typ":"JWT"}
```

`alg` is exactly `HS256`; `kid` matches `[A-Za-z0-9._-]{1,64}` and selects one configured key;
`typ` is exactly `JWT`. Duplicate names, unknown critical headers, malformed base64url, or non-object
JSON fail.

## Claims

```json
{
  "ver": 1,
  "iss": "legacy-jobtrace",
  "aud": "jobtrace-java",
  "sub": "legacy-user-id",
  "role": "user",
  "av": 3,
  "rid": "0199...-uuid",
  "mth": "GET",
  "pth": "/api/analytics/summary",
  "iat": 1790784000,
  "nbf": 1790784000,
  "exp": 1790784030,
  "jti": "base64url-128-bit-random-value"
}
```

All claims are mandatory. `aud` is one string. Times are integer epoch seconds. `exp - iat` is 1–30
seconds. Validation permits at most 5 seconds of skew without extending maximum issuer lifetime.
`sub` is non-blank and at most 128 characters. `role` is `user` or `admin`; `av` is non-negative.
`rid` equals the effective request ID. `mth` and `pth` exactly match the request. `jti` provides at
least 128 bits of issuer-generated randomness.

## Signing and replay

The signature is standard HMAC SHA-256 over compact-JWS signing input using the `kid` secret. Each
secret decodes to at least 32 random bytes. After cryptographic and claim validation, Java atomically
creates replay state for `iss + jti` only if absent, with TTL through expiry plus skew. Duplicate,
timeout, or storage failure denies authentication; concurrent claims permit exactly one request.

## Authentication result

Success creates a principal named by `sub`, with role and access version only from the assertion.
Failure returns HTTP 401 in the repository problem format without revealing which field was valid.
Public identity headers, cookies, query fields, and body fields never affect the principal.

## Key rotation

1. Generate a new random secret of at least 32 bytes and a new key ID in the secret manager.
2. Add it to Java while retaining the previous key.
3. Switch the issuer's current key and verify acceptance.
4. Wait at least 35 seconds and confirm no old assertion remains in flight.
5. Remove the previous key and verify rejection.

No key value may be printed, committed, included in an exception, or exposed to a client.
