# Data Model: Job Market Operations

## Campaign favorite

Key: `(owner_id, campaign_id)`. Creation is idempotent. Removal for a campaign clears that owner's favorites for all campaigns belonging to the same company, matching the existing marketplace projection.

## Company and source

Company is the canonical marketplace identity. Sources belong to companies and carry adapter, external key, URL/allowed hosts, official status, lifecycle status, schedule and health timestamps. Only validated, approved sources are eligible for synchronization.

## Discovery candidates

Source and company candidates preserve normalized identity, provenance, confidence and review state (`pending`, `approved`, `ignored`). Approval is transactional and links the resulting company or source; repeated review cannot create duplicates.

## Sync run and listing

Sync run belongs to a source and records trigger, worker, request ID, start/finish, status, counts and safe error summary. A run lease/fence token controls writes. Normalized posts, source records and event history are updated only by the current lease holder.

## Validation

User identity comes from the bridge principal; admin/internal roles are checked before work. URLs require HTTPS where the legacy adapter requires it, public DNS/IP resolution, redirect revalidation and bounded response size. User-facing errors do not expose fetched page bodies, credentials or infrastructure details.
