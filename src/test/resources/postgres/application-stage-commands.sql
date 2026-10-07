create unique index application_stage_unique_test
on application_stage_occurrences(application_id,stage);

create or replace function add_stage_occurrence_for_owner(
  actor_id text,target_id uuid,stage_code recruitment_stage,occurrence_date date)
returns application_stage_occurrences language plpgsql as $$
declare app applications; result application_stage_occurrences;
begin
  select * into app from applications where id=target_id and owner_id=actor_id for update;
  if not found then raise exception using errcode='P0002',message='application_not_found'; end if;
  if app.status in ('offer','refused') then
    raise exception using errcode='22023',message='terminal_application';
  end if;
  if occurrence_date < app.applied_date or occurrence_date > current_date then
    raise exception using errcode='22023',message='invalid_stage_date';
  end if;
  insert into application_stage_occurrences(id,application_id,stage,occurred_on,created_at)
  values(gen_random_uuid(),app.id,stage_code,occurrence_date,now()) returning * into result;
  update applications set latest_date=greatest(latest_date,occurrence_date),version=version+1,
    updated_at=now() where id=app.id;
  insert into application_events(id,application_id,type,occurred_on,before,after,created_at)
  values(gen_random_uuid(),app.id,'stage_added',occurrence_date,null,to_jsonb(result),now());
  return result;
end $$;

create or replace function update_stage_occurrence_for_owner(
  actor_id text,target_application_id uuid,occurrence_id uuid,expected_version integer,
  stage_code recruitment_stage,occurrence_date date,change_date date)
returns application_stage_occurrences language plpgsql as $$
declare app applications; result application_stage_occurrences;
begin
  select * into app from applications where id=target_application_id and owner_id=actor_id for update;
  if not found then raise exception using errcode='P0002',message='stage_not_found'; end if;
  if app.version<>expected_version then
    raise exception using errcode='40001',message='application_version_conflict';
  end if;
  if occurrence_date < app.applied_date or occurrence_date > current_date then
    raise exception using errcode='22023',message='invalid_stage_date';
  end if;
  update application_stage_occurrences set stage=stage_code,occurred_on=occurrence_date
    where id=occurrence_id and application_id=app.id returning * into result;
  if not found then raise exception using errcode='P0002',message='stage_not_found'; end if;
  update applications set latest_date=greatest(latest_date,change_date),version=version+1,
    updated_at=now() where id=app.id;
  insert into application_events(id,application_id,type,occurred_on,before,after,created_at)
  values(gen_random_uuid(),app.id,'details_changed',change_date,null,to_jsonb(result),now());
  return result;
end $$;

create or replace function remove_stage_occurrence_for_owner(
  actor_id text,target_id uuid,occurrence_id uuid,change_date date)
returns void language plpgsql as $$
declare app applications; old_row application_stage_occurrences;
begin
  select * into app from applications where id=target_id and owner_id=actor_id for update;
  if not found then raise exception using errcode='P0002',message='stage_not_found'; end if;
  delete from application_stage_occurrences where id=occurrence_id and application_id=app.id
    returning * into old_row;
  if not found then raise exception using errcode='P0002',message='stage_not_found'; end if;
  update applications set latest_date=greatest(latest_date,change_date),version=version+1,
    updated_at=now() where id=app.id;
  insert into application_events(id,application_id,type,occurred_on,before,after,created_at)
  values(gen_random_uuid(),app.id,'stage_removed',change_date,to_jsonb(old_row),'{}',now());
end $$;
