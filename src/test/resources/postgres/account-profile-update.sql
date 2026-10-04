create table users (
  id text primary key,
  display_name text not null,
  email text not null,
  email_verified boolean not null default false,
  image text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  role text not null default 'user',
  disabled boolean not null default false,
  access_version bigint not null default 1,
  recovery_email text,
  recovery_email_verified_at timestamptz
);
