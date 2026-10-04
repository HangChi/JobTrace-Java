create table users (
  id text primary key,
  username text,
  display_username text,
  email text not null,
  role text not null check (role in ('user','admin')),
  disabled boolean not null default false,
  access_version bigint not null default 1 check (access_version > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table sessions (
  id text primary key,
  user_id text not null references users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table applications (
  id text primary key,
  owner_id text not null references users(id) on delete cascade
);

create table interview_reviews (
  id text primary key,
  owner_id text not null references users(id) on delete cascade
);

create table admin_audit_events (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null unique,
  request_fingerprint text not null,
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
  created_at timestamptz not null default now()
);

create or replace function public.change_user_access_as(
  actor text,
  target text,
  command_request_id uuid,
  expected_version bigint,
  command_action text,
  command_reason text,
  confirm_self boolean default false
) returns jsonb
language plpgsql security definer set search_path=public as $$
declare
  actor_row public.users;
  target_row public.users;
  result_row public.users;
  existing_event public.admin_audit_events;
  event_id uuid;
  request_fingerprint text;
  result_outcome text;
  result_code text;
  next_role text;
  next_disabled boolean;
  before_state jsonb;
  after_state jsonb;
  active_admins integer;
begin
  perform pg_advisory_xact_lock(
    hashtextextended('admin-access-request:' || command_request_id::text,0));

  if command_action not in ('promote_admin','demote_admin','disable_user','enable_user') then
    raise exception using errcode='22023',message='invalid_admin_action';
  end if;
  if char_length(trim(command_reason)) not between 10 and 500 then
    raise exception using errcode='22023',message='invalid_admin_reason';
  end if;

  request_fingerprint := md5(concat_ws('|',target,expected_version,command_action,
    trim(command_reason),confirm_self));
  select * into existing_event from admin_audit_events where request_id=command_request_id;
  if found then
    if existing_event.request_fingerprint<>request_fingerprint then
      return jsonb_build_object('outcome','conflict','failureCode','idempotency_conflict',
        'auditEventId',existing_event.id,'replayed',false);
    end if;
    select * into result_row from users where id=target;
    return jsonb_build_object('outcome',existing_event.outcome,
      'failureCode',existing_event.failure_code,'auditEventId',existing_event.id,
      'replayed',true,'userId',result_row.id,'role',result_row.role,
      'disabled',result_row.disabled,'accessVersion',result_row.access_version);
  end if;

  lock table public.users in share row exclusive mode;
  select * into actor_row from users where id=actor for update;
  if not found or actor_row.role<>'admin' or actor_row.disabled then
    raise exception using errcode='42501',message='admin_required';
  end if;
  select * into target_row from users where id=target for update;
  if not found then raise exception using errcode='P0002',message='user_not_found'; end if;

  before_state := jsonb_build_object('role',target_row.role,'disabled',target_row.disabled,
    'accessVersion',target_row.access_version);
  next_role := target_row.role;
  next_disabled := target_row.disabled;
  result_outcome := 'succeeded';
  result_code := null;

  if target_row.access_version<>expected_version then
    result_outcome := 'conflict'; result_code := 'access_version_conflict';
  elsif command_action='promote_admin' and (target_row.role='admin' or target_row.disabled) then
    result_outcome := 'conflict'; result_code := 'action_state_conflict';
  elsif command_action='demote_admin' and target_row.role<>'admin' then
    result_outcome := 'conflict'; result_code := 'action_state_conflict';
  elsif command_action='disable_user' and target_row.disabled then
    result_outcome := 'conflict'; result_code := 'action_state_conflict';
  elsif command_action='enable_user' and not target_row.disabled then
    result_outcome := 'conflict'; result_code := 'action_state_conflict';
  elsif actor=target and command_action in ('demote_admin','disable_user') and not confirm_self then
    result_outcome := 'denied'; result_code := 'self_confirmation_required';
  else
    if command_action='promote_admin' then next_role := 'admin'; end if;
    if command_action='demote_admin' then next_role := 'user'; end if;
    if command_action='disable_user' then next_disabled := true; end if;
    if command_action='enable_user' then next_disabled := false; end if;
    if target_row.role='admin' and not target_row.disabled
      and (next_role<>'admin' or next_disabled) then
      select count(*) into active_admins from users where role='admin' and disabled=false;
      if active_admins<=1 then
        result_outcome := 'denied'; result_code := 'last_admin';
      end if;
    end if;
  end if;

  if result_outcome='succeeded' then
    update users set role=next_role,disabled=next_disabled,
      access_version=access_version+1,updated_at=now()
    where id=target returning * into result_row;
    if command_action='disable_user' then delete from sessions where user_id=target; end if;
    after_state := jsonb_build_object('role',result_row.role,'disabled',result_row.disabled,
      'accessVersion',result_row.access_version);
  else
    result_row := target_row;
    after_state := before_state;
  end if;

  insert into admin_audit_events(
    request_id,request_fingerprint,actor_id,actor_identifier_snapshot,
    target_user_id,target_identifier_snapshot,event_type,outcome,reason,
    before_data,after_data,failure_code
  ) values(
    command_request_id,request_fingerprint,actor_row.id,
    coalesce(actor_row.display_username,actor_row.username,actor_row.email),
    target_row.id,coalesce(target_row.display_username,target_row.username,target_row.email),
    command_action,result_outcome,trim(command_reason),before_state,after_state,result_code
  ) returning id into event_id;

  return jsonb_build_object('outcome',result_outcome,'failureCode',result_code,
    'auditEventId',event_id,'replayed',false,'userId',result_row.id,
    'role',result_row.role,'disabled',result_row.disabled,
    'accessVersion',result_row.access_version);
end $$;
