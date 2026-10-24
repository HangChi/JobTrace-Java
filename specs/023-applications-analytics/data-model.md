# Data model

The legacy database remains authoritative. `applications` is owned by `owner_id`, carries `version`, status, dates and description. `application_stage_occurrences` belongs to an application; `application_events` stores its history. Application and stage changes call existing owner-aware SQL functions so their constraints, version increments and event writes remain atomic. Job-market links are created only after validation in the same transaction as the application.

The analytics report is a read projection over owner-filtered applications, stage occurrences and interview reviews. A progress resolution belongs to an owner and stage occurrence. No new table or migration is introduced.
