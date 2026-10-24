-- Test-only projection of the legacy public sharing and engagement schema.
alter table interview_reviews
  add column like_count integer not null default 0,
  add column comment_count integer not null default 0,
  add column view_count integer not null default 0,
  add column hot_score bigint generated always as
    (like_count::bigint * 3 + comment_count::bigint * 2 + view_count::bigint) stored@@

create table users (
  id text primary key, username text, display_username text,
  display_name text, image text)@@
create table interview_review_likes (
  interview_review_id uuid not null references interview_reviews(id) on delete cascade,
  user_id text not null references users(id),
  primary key(interview_review_id,user_id))@@
create table interview_review_comments (
  id uuid primary key default gen_random_uuid(),
  interview_review_id uuid not null references interview_reviews(id) on delete cascade,
  user_id text not null references users(id),
  content text not null, created_at timestamptz not null default now())@@
create table interview_review_views (
  interview_review_id uuid not null references interview_reviews(id) on delete cascade,
  user_id text not null references users(id),
  primary key(interview_review_id,user_id))@@

create or replace function assert_public_interview(target_id uuid)
returns interview_reviews language plpgsql as $$
declare result interview_reviews;
begin
  select * into result from interview_reviews
  where id=target_id and visibility='public' and status='completed'
    and published_at is not null for update;
  if not found then raise exception using errcode='P0002'; end if;
  return result;
end $$@@

create or replace function record_public_interview_view(actor_id text,target_id uuid)
returns table(like_count integer,comment_count integer,view_count integer)
language plpgsql as $$
begin
  perform assert_public_interview(target_id);
  insert into interview_review_views values(target_id,actor_id) on conflict do nothing;
  update interview_reviews r set view_count=(select count(*) from interview_review_views v
    where v.interview_review_id=target_id) where r.id=target_id;
  return query select r.like_count,r.comment_count,r.view_count
    from interview_reviews r where r.id=target_id;
end $$@@

create or replace function toggle_public_interview_like(actor_id text,target_id uuid)
returns table(liked boolean,like_count integer,comment_count integer,view_count integer)
language plpgsql as $$
begin
  perform assert_public_interview(target_id);
  if exists(select 1 from interview_review_likes l
      where l.interview_review_id=target_id and l.user_id=actor_id) then
    delete from interview_review_likes l where l.interview_review_id=target_id
      and l.user_id=actor_id;
    liked:=false;
  else
    insert into interview_review_likes values(target_id,actor_id);
    liked:=true;
  end if;
  update interview_reviews r set like_count=(select count(*) from interview_review_likes l
    where l.interview_review_id=target_id) where r.id=target_id;
  return query select liked,r.like_count,r.comment_count,r.view_count
    from interview_reviews r where r.id=target_id;
end $$@@

create or replace function add_public_interview_comment(
  actor_id text,target_id uuid,comment_content text)
returns interview_review_comments language plpgsql as $$
declare result interview_review_comments;
begin
  perform assert_public_interview(target_id);
  if char_length(trim(comment_content)) not between 1 and 1000 then
    raise exception using errcode='22023';
  end if;
  insert into interview_review_comments(interview_review_id,user_id,content)
  values(target_id,actor_id,trim(comment_content)) returning * into result;
  update interview_reviews r set comment_count=(select count(*) from interview_review_comments c
    where c.interview_review_id=target_id) where r.id=target_id;
  return result;
end $$@@
