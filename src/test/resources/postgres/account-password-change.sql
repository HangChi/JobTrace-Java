create table users (
  id text primary key,
  display_name text not null,
  email text not null unique,
  email_verified boolean not null default false,
  image text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table accounts (
  id text primary key,
  account_id text not null,
  provider_id text not null,
  user_id text not null references users(id) on delete cascade,
  password text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index accounts_user_id_idx on accounts(user_id);

create table sessions (
  id text primary key,
  expires_at timestamptz not null,
  token text not null unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  user_id text not null references users(id) on delete cascade
);
create index sessions_user_id_idx on sessions(user_id);
