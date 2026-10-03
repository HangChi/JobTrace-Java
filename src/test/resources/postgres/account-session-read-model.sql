create table users (
  id text primary key
);

create table sessions (
  id text primary key,
  expires_at timestamptz not null,
  token text not null unique,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  ip_address text,
  user_agent text,
  user_id text not null references users(id) on delete cascade,
  impersonated_by text
);

create index sessions_user_id_idx on sessions(user_id);
