# Data Model: Job Market Operations

## Campaign favorite

Key: `(owner_id, campaign_id)`. Creation is idempotent. Removal for a campaign clears that owner's favorites for all campaigns belonging to the same company, matching the existing marketplace projection.

## Company and source

Company is the canonical marketplace identity. Sources belong to companies and carry adapter, external key, URL/allowed hosts, official status, lifecycle status, schedule and health timestamps. Only validated, approved sources are eligible for synchronization.

## Discovery candidates

Source and company candidates preserve normalized identity, provenance, confidence and review state (`pending`, `approved`, `ignored`). Approval is transactional and links the resulting company or source; repeated review cannot create duplicates.

## Sync run and listing

Sync run belongs to a source and records trigger, worker, request ID, start/finish, status, counts and safe error summary. The legacy worker owns its lease/fence token and updates normalized posts, source records and event history. Java reads these rows without claiming or completing a run.

## Validation

User identity comes from the bridge principal; administrator role is checked before curation. Manually submitted source URLs require approved HTTPS hosts. Java does not resolve or fetch marketplace URLs. User-facing errors do not expose credentials or infrastructure details.
