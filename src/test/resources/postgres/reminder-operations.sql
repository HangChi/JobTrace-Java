-- Test-only extension of the established reminder read fixture.
alter table users add column updated_at timestamptz not null default now();
alter table scheduled_reminders add column updated_at timestamptz not null default now();
alter table scheduled_reminders alter column id set default gen_random_uuid();
alter table reminder_notification_attempts
  add column claim_token uuid,
  add column lease_until timestamptz,
  add column attempt_count integer not null default 1,
  add column recipient text,
  add column error_code text,
  add column sent_at timestamptz,
  add column created_at timestamptz not null default now();
alter table reminder_notification_attempts alter column id set default gen_random_uuid();
alter table scheduled_reminders drop constraint scheduled_reminders_check;
create table application_stage_occurrences (
  id uuid primary key,
  application_id uuid not null references applications(id)
);
create table progress_reminder_completions (
  id uuid primary key default gen_random_uuid(),
  owner_id text not null,
  stage_occurrence_id uuid not null references application_stage_occurrences(id),
  resolution text not null,
  reminder_id uuid references scheduled_reminders(id) on delete set null,
  updated_at timestamptz not null default now(),
  unique(owner_id,stage_occurrence_id)
);
insert into application_stage_occurrences(id,application_id) values
('00000000-0000-0000-0000-000000000401','00000000-0000-0000-0000-000000000201'),
('00000000-0000-0000-0000-000000000402','00000000-0000-0000-0000-000000000202');
