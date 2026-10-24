create table users (
  id text primary key,
  username text,
  display_username text,
  email text not null,
  recovery_email text,
  role text not null check (role in ('user','admin')),
  disabled boolean not null default false,
  access_version bigint not null default 1,
  created_at timestamptz not null
);

create table sessions (
  id text primary key,
  user_id text not null references users(id) on delete cascade,
  created_at timestamptz not null
);

create table applications (
  id text primary key,
  owner_id text not null references users(id) on delete cascade,
  company_name text not null,
  position_name text not null,
  city text,
  job_url text,
  type text not null,
  status text not null,
  applied_date date not null,
  latest_date date not null,
  notes text
);

create table application_stage_occurrences (
  id text primary key,
  application_id text not null references applications(id) on delete cascade,
  stage text not null,
  occurred_on date not null,
  created_at timestamptz not null
);

create table interview_reviews (
  id text primary key,
  owner_id text not null references users(id) on delete cascade,
  application_id text not null references applications(id) on delete cascade,
  stage_occurrence_id text references application_stage_occurrences(id) on delete set null,
  stage_snapshot text not null,
  interviewed_on date not null,
  status text not null,
  round_result text not null,
  format text,
  duration_minutes integer,
  interviewer_notes text,
  highlights text,
  gaps text
);

create table interview_questions (
  id text primary key,
  interview_review_id text not null references interview_reviews(id) on delete cascade,
  category text not null,
  question text not null,
  original_answer text,
  follow_up_notes text,
  improved_answer text,
  self_rating integer,
  sort_order integer not null
);

create table interview_action_items (
  id text primary key,
  interview_review_id text not null references interview_reviews(id) on delete cascade,
  content text not null,
  completed boolean not null,
  sort_order integer not null
);

create table admin_audit_events (
  id text primary key,
  request_id text not null unique,
  actor_id text references users(id) on delete set null,
  actor_identifier_snapshot text not null,
  target_user_id text references users(id) on delete set null,
  target_identifier_snapshot text not null,
  event_type text not null,
  outcome text not null,
  reason text not null,
  before_data jsonb not null,
  after_data jsonb,
  failure_code text,
  created_at timestamptz not null
);

create index admin_users_created_idx on users(created_at desc,id desc);
create index admin_audit_created_idx on admin_audit_events(created_at desc,id desc);
create index admin_audit_target_idx on admin_audit_events(target_user_id,created_at desc,id desc);
