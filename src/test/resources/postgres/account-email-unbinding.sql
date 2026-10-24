alter table users add column recovery_email text;
alter table users add column recovery_email_verified_at timestamptz;

create table email_verification_codes (
  id uuid primary key,
  email text not null,
  purpose text not null,
  user_id text references users(id) on delete cascade,
  code_hash text not null,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  attempt_count integer not null default 0,
  created_at timestamptz not null default now()
);

drop table if exists auth_rate_limits;
create table auth_rate_limits (
  key_hash text not null,
  action text not null,
  window_started_at timestamptz not null,
  request_count integer not null check(request_count > 0),
  primary key(key_hash,action)
);
