create table users (id text primary key);

create table applications (
  id uuid primary key,
  owner_id text not null references users(id)
);

create table job_market_companies (
  id uuid primary key,
  canonical_name text not null,
  normalized_name text not null default '',
  identity_key text not null unique default gen_random_uuid()::text,
  company_type text,
  industry text,
  website_url text
);

create table job_market_campaigns (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references job_market_companies(id) on delete cascade,
  campaign_key text not null default gen_random_uuid()::text,
  name text,
  listing_kind text not null default 'synced_jobs',
  recruitment_type text,
  status text not null default 'open',
  official_apply_url text,
  published_at timestamptz,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table job_market_sources (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references job_market_companies(id) on delete cascade,
  adapter text not null,
  catalog_key text unique,
  external_key text not null default '',
  base_url text,
  allowed_hosts text[] not null default '{}',
  country_codes text[] not null default '{}',
  is_official boolean not null default true,
  access_basis text not null default 'public',
  status text not null default 'active',
  sync_interval_minutes integer not null default 360,
  next_sync_at timestamptz not null default now(),
  lease_until timestamptz,
  leased_by text,
  lease_run_id uuid,
  consecutive_failures integer not null default 0,
  last_attempt_at timestamptz,
  last_success_at timestamptz,
  updated_at timestamptz not null default now(),
  unique(company_id, adapter, external_key)
);

create table job_market_sync_runs (
  id uuid primary key default gen_random_uuid(),
  source_id uuid not null references job_market_sources(id) on delete cascade,
  trigger text not null,
  worker_id text not null default '',
  request_id text not null default '',
  status text not null default 'running',
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  discovered_count integer not null default 0,
  created_count integer not null default 0,
  updated_count integer not null default 0,
  stale_count integer not null default 0,
  closed_count integer not null default 0,
  rejected_count integer not null default 0,
  error_code text,
  error_summary text
);

create table job_market_source_candidates (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references job_market_companies(id) on delete cascade,
  entry_url text not null,
  adapter text,
  external_key text,
  base_url text,
  allowed_hosts text[] not null default '{}',
  confidence text,
  evidence_code text not null,
  review_status text not null default 'unrecognized',
  health_status text not null,
  diagnostic_code text,
  diagnostic_summary text,
  http_status integer,
  approved_source_id uuid references job_market_sources(id),
  last_checked_at timestamptz not null default now(),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(company_id, entry_url)
);

create table job_market_company_candidates (
  id uuid primary key default gen_random_uuid(),
  company_name text not null,
  normalized_name text not null unique,
  article_url text not null,
  article_title text not null,
  snippet text,
  published_at timestamptz,
  source_engine text not null,
  article_count integer not null default 1,
  detected_adapter text,
  detected_external_key text,
  detected_base_url text,
  detected_allowed_hosts text[] not null default '{}',
  detected_confidence text,
  review_status text not null default 'pending',
  created_company_id uuid references job_market_companies(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table job_market_posts (
  id uuid primary key,
  company_id uuid not null references job_market_companies(id) on delete cascade,
  campaign_id uuid references job_market_campaigns(id) on delete set null,
  title text not null,
  status text not null default 'open',
  primary_apply_url text,
  published_at timestamptz,
  valid_through timestamptz
);

create table job_market_source_records (
  source_id uuid not null references job_market_sources(id) on delete cascade,
  post_id uuid not null references job_market_posts(id) on delete cascade,
  last_seen_at timestamptz not null default now(),
  primary key(source_id, post_id)
);

create table job_market_locations (
  id uuid primary key,
  display_name text not null,
  is_remote boolean not null default false
);

create table job_market_post_locations (
  post_id uuid not null references job_market_posts(id) on delete cascade,
  location_id uuid not null references job_market_locations(id) on delete cascade,
  primary key(post_id, location_id)
);

create table job_market_campaign_favorites (
  owner_id text not null references users(id) on delete cascade,
  campaign_id uuid not null references job_market_campaigns(id) on delete cascade,
  primary key(owner_id, campaign_id)
);

create table application_job_market_links (
  owner_id text not null references users(id) on delete cascade,
  post_id uuid not null references job_market_posts(id) on delete cascade,
  application_id uuid not null references applications(id) on delete cascade,
  primary key(owner_id, post_id)
);

create table job_market_company_read_models (
  company_id uuid not null references job_market_companies(id) on delete cascade,
  include_closed boolean not null,
  representative_campaign_id uuid not null references job_market_campaigns(id) on delete cascade,
  listing_kind text not null,
  recruitment_type text,
  positions text[] not null default '{}',
  position_count integer not null default 0,
  locations jsonb not null default '[]'::jsonb,
  status text not null,
  primary_apply_url text,
  source_name text,
  source_url text,
  published_at timestamptz,
  valid_through timestamptz,
  last_confirmed_at timestamptz,
  search_text text not null default '',
  location_text text not null default '',
  primary key(company_id, include_closed)
);

create index job_market_company_read_model_browse_idx
  on job_market_company_read_models(include_closed, published_at desc, last_confirmed_at desc, company_id);
create index job_market_company_read_model_status_idx
  on job_market_company_read_models(include_closed, status, published_at desc, company_id);
create index job_market_posts_company_status_idx
  on job_market_posts(company_id, status, published_at desc, id);
create index job_market_source_records_post_seen_idx
  on job_market_source_records(post_id, last_seen_at desc, source_id);
create index job_market_post_locations_post_idx
  on job_market_post_locations(post_id, location_id);
create index application_job_market_links_post_owner_idx
  on application_job_market_links(post_id, owner_id);
create index job_market_campaign_favorites_campaign_owner_idx
  on job_market_campaign_favorites(campaign_id, owner_id);
