-- Test-only projection of the legacy read schema at the 005 baseline.
-- Never run this against an existing JobTrace database.
create type public.application_status as enum ('submitted', 'offer', 'refused');
create type public.application_type as enum (
  'summer_internship', 'daily_internship', 'spring_recruitment',
  'early_campus_recruitment', 'campus_recruitment', 'social_recruitment');
create type public.recruitment_stage as enum (
  'screening', 'assessment', 'written_test', 'interview_1', 'interview_2',
  'interview_3', 'hr_interview', 'final_interview');
create type public.interview_format as enum ('online', 'offline', 'phone');
create type public.round_result as enum ('pending', 'passed', 'failed');
create type public.review_status as enum ('draft', 'pending_review', 'completed');
create type public.question_category as enum (
  'technical', 'project', 'behavioral', 'system_design', 'other');
create type public.interview_visibility as enum ('private', 'public');
create type public.interview_author_mode as enum ('anonymous', 'attributed');
create type public.application_event_type as enum (
  'created', 'details_changed', 'status_changed', 'stage_added',
  'stage_removed', 'imported');

create table public.applications (
  id uuid primary key, owner_id text not null,
  company_name text not null, position_name text not null, city text, job_url text,
  applied_date date not null, type public.application_type not null,
  status public.application_status not null, notes text,
  latest_date date not null, version integer not null,
  created_at timestamptz not null, updated_at timestamptz not null);
create table public.application_stage_occurrences (
  id uuid primary key, application_id uuid not null references public.applications(id),
  stage public.recruitment_stage not null, occurred_on date not null,
  created_at timestamptz not null);
create table public.application_events (
  id uuid primary key, application_id uuid not null references public.applications(id),
  type public.application_event_type not null, occurred_on date not null,
  before jsonb, after jsonb not null, created_at timestamptz not null);
create table public.interview_reviews (
  id uuid primary key, owner_id text not null,
  application_id uuid not null references public.applications(id),
  stage_occurrence_id uuid references public.application_stage_occurrences(id) on delete set null,
  stage_snapshot public.recruitment_stage not null,
  interviewed_on date not null, format public.interview_format,
  duration_minutes integer, interviewer_notes text,
  round_result public.round_result not null, highlights text, gaps text,
  status public.review_status not null, visibility public.interview_visibility not null default 'private',
  author_mode public.interview_author_mode not null default 'anonymous',
  published_at timestamptz, version integer not null,
  created_at timestamptz not null, updated_at timestamptz not null);
create table public.interview_questions (
  id uuid primary key, interview_review_id uuid not null references public.interview_reviews(id),
  sort_order integer not null, category public.question_category not null,
  question text not null, original_answer text, follow_up_notes text,
  improved_answer text, self_rating integer);
create table public.interview_action_items (
  id uuid primary key, interview_review_id uuid not null references public.interview_reviews(id),
  sort_order integer not null, content text not null, completed boolean not null);

create index interview_reviews_owner_date_idx
  on public.interview_reviews(owner_id, interviewed_on desc, id desc);
create index interview_reviews_owner_status_idx
  on public.interview_reviews(owner_id, status, interviewed_on desc, id desc);
create index interview_reviews_owner_stage_idx
  on public.interview_reviews(owner_id, stage_snapshot, interviewed_on desc, id desc);
create index interview_reviews_application_idx
  on public.interview_reviews(application_id, interviewed_on desc, id desc);
create index interview_questions_review_idx
  on public.interview_questions(interview_review_id, sort_order);
create index interview_action_items_review_idx
  on public.interview_action_items(interview_review_id, sort_order);
