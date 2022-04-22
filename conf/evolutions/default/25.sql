# --!Ups
alter table "FileEntry" add column i_premiere_version int null;
create table "PremiereVersionTranslation" (i_internal integer not null primary key, s_name character varying not null, s_displayed_version character varying not null);

-- NOTE - displayed version is not verified and might need to be changed
insert into "PremiereVersionTranslation" (i_internal, s_name, s_displayed_version) values (35, 'Adobe Premiere Pro CC 2019', '13.0.0') on conflict do nothing;

# --!Downs
alter table "FileEntry" drop column i_premiere_version;
drop table "PremiereVersionTranslation";