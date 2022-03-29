# --!Ups
alter table "FileEntry" add column i_premiere_version int null;

# --!Downs
alter table "FileEntry" drop column i_premiere_version;
