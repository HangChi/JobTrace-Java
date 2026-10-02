-- Test-only projection of the legacy reminder read schema. Never run in production.
create table public.users (
  id text primary key,
  recovery_email text,
  recovery_email_verified_at timestamptz,
  reminder_home_enabled boolean not null default true,
  reminder_home_view text not null default 'scheduled'
    check (reminder_home_view in ('scheduled', 'suggestions')),
  reminder_default_lead text not null default '1h'
    check (reminder_default_lead in ('on_time', '30m', '1h', '1d')),
  reminder_default_snooze_minutes integer not null default 30
    check (reminder_default_snooze_minutes in (30, 60, 1440)),
  reminder_email_default boolean not null default false
);
create table public.applications (
  id uuid primary key,
  owner_id text not null references public.users(id),
  company_name text not null,
  position_name text not null
);
create table public.scheduled_reminders (
  id uuid primary key,
  owner_id text not null references public.users(id),
  application_id uuid not null references public.applications(id),
  source_stage_occurrence_id uuid,
  title text not null check (char_length(btrim(title)) between 1 and 120),
  event_at timestamptz not null,
  notify_at timestamptz not null,
  email_enabled boolean not null default false,
  status text not null default 'pending'
    check (status in ('pending', 'due', 'completed', 'cancelled')),
  completed_at timestamptz,
  cancelled_at timestamptz,
  version integer not null default 1 check (version >= 1),
  check (notify_at <= event_at),
  check ((status = 'completed') = (completed_at is not null)),
  check ((status = 'cancelled') = (cancelled_at is not null))
);
create index scheduled_reminders_owner_status_notify_idx
  on public.scheduled_reminders(owner_id, status, notify_at, id);
create table public.reminder_notification_attempts (
  id uuid primary key,
  reminder_id uuid not null references public.scheduled_reminders(id),
  owner_id text not null references public.users(id),
  channel text not null check (channel in ('email')),
  scheduled_for timestamptz not null,
  status text not null check (status in ('claimed', 'sent', 'failed', 'skipped')),
  updated_at timestamptz not null default now(),
  unique(reminder_id, scheduled_for, channel)
);
