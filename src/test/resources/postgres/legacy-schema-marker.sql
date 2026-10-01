create table legacy_schema_marker (
    id integer primary key,
    legacy_head text not null
);

insert into legacy_schema_marker(id, legacy_head)
values (1, '20260929000100_spring_recruitment_type.sql');
