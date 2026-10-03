# Data Model: Job Market Read Model Migration

Feature 008 creates no production table, migration or durable state. The objects below are immutable request criteria and read projections over legacy-owned PostgreSQL data. The current service remains the only writer and projection maintainer.

## MarketplaceQuery

| Field | Validation and meaning |
| --- | --- |
| `q` | Optional trimmed text, blank becomes absent, maximum 100 characters; case-insensitive match against company/position search text |
| `company` | Optional trimmed text, blank becomes absent, maximum 100 characters; case-insensitive company-name match |
| `location` | Optional trimmed text, blank becomes absent, maximum 100 characters; case-insensitive location match |
| `status` | Optional `open`, `stale`, or `closed` |
| `postedFrom` | Optional ISO local date; inclusive lower bound against publication date |
| `favorite` | Optional strict boolean query value; true selects favorites and enables closed projection eligibility, false behaves like omission |
| `page` | Integer ≥1, default 1 |
| `limit` | Integer 1–100, default 20 |

The authenticated owner is supplied separately by the trusted bridge. It is never accepted from this query. `includeClosed` is derived as `favorite == true || status == closed`, never supplied by the caller.

## Company

| Field | Type |
| --- | --- |
| `id` | UUID |
| `name` | Non-empty string |
| `type` | Nullable string |
| `industry` | Nullable string |

This shared marketplace identity comes from `job_market_companies`. It has no ownership relation to the requesting user.

## CampaignLocation

| Field | Type |
| --- | --- |
| `name` | Non-empty display string |
| `isRemote` | Boolean |

Locations are deduplicated and deterministically name-ordered by the maintained company projection or the detail aggregate.

## CampaignSummary

| Field | Meaning |
| --- | --- |
| `id` | UUID of the representative campaign for the company projection |
| `listingKind` | `synced_jobs` or `recruitment_directory` |
| `company` | Shared company projection |
| `campaignName` | Nullable compatibility field; currently null for the company projection |
| `recruitmentType` | Nullable recruitment label |
| `batchLabel` | Nullable compatibility field; currently null for the company projection |
| `positions` | Deterministically ordered position names; list preview is capped at 50, detail summary is complete |
| `positionCount` | Complete distinct normalized-position count, never reduced by preview capping |
| `locations` | Aggregated company locations |
| `status` | `open`, `stale`, or `closed` |
| `applyMode` | `single`, `select`, or `unavailable`; current projection maps a safe campaign target to `single`, otherwise `unavailable` |
| `primaryApplyUrl` | Nullable canonical HTTPS target |
| `source` | Source name plus nullable canonical HTTPS URL |
| `publishedAt`, `validThrough`, `lastConfirmedAt` | Nullable instants serialized in ISO-8601 form |
| `isFavorite` | Boolean derived only from favorite rows owned by the authenticated user for any campaign of this company |

Summaries are ordered by publication descending/nulls last, last confirmation descending/nulls last, then company UUID ascending. One summary represents one company. Default and stale/open queries use the no-closed projection; explicit closed or favorite-only reads use the include-closed projection.

## CampaignPage

| Field | Meaning |
| --- | --- |
| `items` | Zero to `limit` campaign summaries |
| `page` | Normalized requested page |
| `limit` | Normalized page size |
| `total` | Full filtered company count before offset/limit |

An out-of-range page returns an empty `items` array while retaining its requested page, limit and correct total.

## CampaignJob

| Field | Meaning |
| --- | --- |
| `id` | Job UUID |
| `title` | Position title |
| `locations` | Deduplicated deterministic locations |
| `status` | `open` or `stale`; closed jobs are excluded from detail |
| `applyUrl` | Nullable canonical HTTPS target |
| `applyUnavailableReason` | Null when actionable; otherwise one of the established Chinese safety/lifecycle reasons |
| `publishedAt`, `validThrough` | Nullable ISO-8601 instants |
| `sourceName` | Selected active source adapter name, with established fallback where applicable |
| `sourceUrl` | Nullable canonical HTTPS company/source destination |
| `alreadyTrackedApplicationId` | Nullable application UUID only when linked to this job and authenticated owner |

Jobs require at least one active source record and are ordered by publication descending/nulls last, then title ascending, then job UUID ascending. The selected source prefers official sources, then the most recently seen record. Tracking is projection-only and does not create an application link.

## CampaignDetail

`CampaignDetail` is a complete `CampaignSummary` plus `jobs: CampaignJob[]`. The requested campaign first identifies a company; the returned summary uses that company's current representative projection, matching legacy behavior. A malformed UUID is validation failure. A missing campaign or absent eligible company projection is the same safe not-found result.

## Legacy Storage Relationships

```text
job_market_companies
├── job_market_company_read_models (company_id, include_closed)
├── job_market_campaigns
│   └── job_market_campaign_favorites (owner_id, campaign_id)
└── job_market_posts
    ├── job_market_post_locations ── job_market_locations
    ├── job_market_source_records ── job_market_sources
    └── application_job_market_links (owner_id, post_id, application_id)
```

`job_market_company_read_models` is rebuildable shared state maintained exclusively by the legacy synchronization path. Favorites and application links are private overlays and must be joined with owner predicates.

## State and Mutation Boundaries

There is no Java-owned state transition in 008. Campaign/job lifecycle, source status, favorite state, tracking links and projection refresh are observed only. Java does not lock, refresh, insert, update, delete or invoke mutating functions. Successful responses are `private, no-store`; storage or identity failure returns a safe problem without partial personalized content.
