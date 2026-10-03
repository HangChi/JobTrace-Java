create table users (id text primary key);

create table applications (
  id uuid primary key,
  owner_id text not null references users(id)
);

create table job_market_companies (
  id uuid primary key,
  canonical_name text not null,
  company_type text,
  industry text,
  website_url text
);

create table job_market_campaigns (
  id uuid primary key,
  company_id uuid not null references job_market_companies(id) on delete cascade,
  listing_kind text not null default 'synced_jobs',
  recruitment_type text,
  status text not null default 'open',
  created_at timestamptz not null default now()
);

create table job_market_sources (
  id uuid primary key,
  company_id uuid not null references job_market_companies(id) on delete cascade,
  adapter text not null,
  base_url text,
  is_official boolean not null default true,
  status text not null default 'active',
  last_success_at timestamptz
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
