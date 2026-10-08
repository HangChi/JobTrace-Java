create table users(id text primary key, disabled boolean not null default false);@@
insert into users(id) values ('owner-a'),('owner-b');@@

create type import_batch_status as enum ('previewed','processing','completed','expired');@@
create type import_row_result as enum ('pending','created','skipped','failed');@@
create table import_batches (
  id uuid primary key default gen_random_uuid(),
  owner_id text not null references users(id),
  status import_batch_status not null default 'previewed',
  columns jsonb not null default '{}'::jsonb,
  file_name varchar(255) not null default 'import',
  format text not null default 'csv' check (format in ('csv','xlsx')),
  total_rows integer not null check (total_rows between 0 and 10000),
  valid_rows integer not null default 0,
  invalid_rows integer not null default 0,
  duplicate_rows integer not null default 0,
  expires_at timestamptz not null default (now() + interval '24 hours'),
  created_at timestamptz not null default now(),
  completed_at timestamptz
);@@
create table import_rows (
  batch_id uuid not null references import_batches(id) on delete cascade,
  row_number integer not null,
  normalized_data jsonb,
  errors jsonb not null default '[]'::jsonb,
  duplicate_application_ids uuid[] not null default '{}',
  decision text check (decision in ('import','skip')),
  result import_row_result not null default 'pending',
  application_id uuid references applications(id) on delete set null,
  primary key(batch_id,row_number)
);@@

create or replace function create_application_for_owner(
  actor_id text,payload jsonb,event_type application_event_type default 'created')
returns applications language plpgsql as $$
declare result applications; stage_row jsonb;
begin
  insert into applications(id,owner_id,company_name,position_name,city,job_url,
    applied_date,type,status,notes,latest_date,version,created_at,updated_at)
  values(gen_random_uuid(),actor_id,payload->>'companyName',payload->>'positionName',
    payload->>'city',payload->>'jobUrl',(payload->>'appliedDate')::date,
    (payload->>'type')::application_type,(payload->>'status')::application_status,
    payload->>'notes',(payload->>'appliedDate')::date,1,now(),now())
  returning * into result;
  insert into application_events(id,application_id,type,occurred_on,before,after,created_at)
  values(gen_random_uuid(),result.id,event_type,result.applied_date,null,to_jsonb(result),now());
  for stage_row in select * from jsonb_array_elements(coalesce(payload->'stages','[]'::jsonb)) loop
    insert into application_stage_occurrences(id,application_id,stage,occurred_on,created_at)
    values(gen_random_uuid(),result.id,(stage_row->>'stage')::recruitment_stage,
      (stage_row->>'occurredOn')::date,now());
    update applications set latest_date=greatest(latest_date,
      (stage_row->>'occurredOn')::date) where id=result.id;
  end loop;
  return result;
end $$;@@

create or replace function confirm_import_batch_for_owner(
  actor_id text,target_batch_id uuid,decisions jsonb)
returns jsonb language plpgsql as $$
declare batch_row import_batches; decision_row record;
  created_application applications; results jsonb := '[]'::jsonb;
begin
  if actor_id is null or not exists(select 1 from users where id=actor_id and disabled=false) then
    raise exception using errcode='42501',message='valid_actor_required';
  end if;
  select * into batch_row from import_batches
  where id=target_batch_id and owner_id=actor_id for update;
  if not found then
    raise exception using errcode='P0002',message='import_batch_not_found';
  end if;
  if batch_row.status <> 'previewed' or batch_row.expires_at <= now() then
    raise exception using errcode='40001',message='import_batch_conflict';
  end if;
  update import_batches set status='processing' where id=target_batch_id;
  for decision_row in
    with raw_decisions as (
      select (value->>'rowNumber')::integer as row_number,
        value->>'action' as action, ordinal
      from jsonb_array_elements(decisions) with ordinality requested(value,ordinal)
    ), requested_decisions as (
      select distinct on(row_number) row_number,action
      from raw_decisions order by row_number,ordinal
    )
    select import_row.*,coalesce(decision.action,'skip') as requested_action
    from import_rows import_row left join requested_decisions decision
      on decision.row_number=import_row.row_number
    where import_row.batch_id=target_batch_id order by import_row.row_number
  loop
    if decision_row.requested_action <> 'import'
      or jsonb_array_length(decision_row.errors) > 0
      or decision_row.normalized_data is null then
      update import_rows set decision='skip',result='skipped',application_id=null
      where batch_id=target_batch_id and row_number=decision_row.row_number;
      results := results || jsonb_build_array(jsonb_build_object(
        'rowNumber',decision_row.row_number,'result','skipped',
        'applicationId',null,'error',null));
      continue;
    end if;
    begin
      select * into created_application from create_application_for_owner(
        actor_id,decision_row.normalized_data,'imported');
      update import_rows set decision='import',result='created',application_id=created_application.id
      where batch_id=target_batch_id and row_number=decision_row.row_number;
      results := results || jsonb_build_array(jsonb_build_object(
        'rowNumber',decision_row.row_number,'result','created',
        'applicationId',created_application.id,'error',null));
    exception when others then
      update import_rows set decision='import',result='failed',application_id=null
      where batch_id=target_batch_id and row_number=decision_row.row_number;
      results := results || jsonb_build_array(jsonb_build_object(
        'rowNumber',decision_row.row_number,'result','failed',
        'applicationId',null,'error',jsonb_build_object(
          'code','import_row_failed','message','该行导入失败。','requestId','')));
    end;
  end loop;
  update import_batches set status='completed',completed_at=now()
  where id=target_batch_id and owner_id=actor_id;
  return results;
end $$;@@
