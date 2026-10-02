create type public.application_status as enum ('submitted', 'offer', 'refused');
create type public.application_type as enum (
  'summer_internship',
  'daily_internship',
  'spring_recruitment',
  'early_campus_recruitment',
  'campus_recruitment',
  'social_recruitment'
);
create type public.recruitment_stage as enum (
  'screening',
  'assessment',
  'written_test',
  'interview_1',
  'interview_2',
  'interview_3',
  'hr_interview',
  'final_interview'
);
create type public.application_event_type as enum (
  'created',
  'details_changed',
  'status_changed',
  'stage_added',
  'stage_removed',
  'imported'
);

create table public.applications (
  id uuid primary key,
  owner_id text not null,
  company_name text not null,
  position_name text not null,
  city text,
  job_url text,
  applied_date date not null,
  type public.application_type not null,
  status public.application_status not null,
  notes text,
  latest_date date not null,
  version integer not null check (version > 0),
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table public.application_stage_occurrences (
  id uuid primary key,
  application_id uuid not null references public.applications(id) on delete cascade,
  stage public.recruitment_stage not null,
  occurred_on date not null,
  created_at timestamptz not null
);

create table public.application_events (
  id uuid primary key,
  application_id uuid not null references public.applications(id) on delete cascade,
  type public.application_event_type not null,
  occurred_on date not null,
  before jsonb,
  after jsonb not null,
  created_at timestamptz not null
);

create index applications_owner_latest_idx
  on public.applications (owner_id, latest_date desc, id desc);
create index application_stages_lookup_idx
  on public.application_stage_occurrences (application_id, occurred_on, created_at);
create index application_events_lookup_idx
  on public.application_events (application_id, occurred_on desc, created_at desc);
