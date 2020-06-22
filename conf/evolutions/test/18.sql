# -- !Ups
INSERT INTO "StorageEntry" (id, S_ROOT_PATH, S_CLIENT_PATH, S_STORAGE_TYPE, S_USER, S_PASSWORD, S_HOST, I_PORT) VALUES (1, '/tmp', NULL, 'Local', 'me', NULL, NULL, NULL);
INSERT INTO "StorageEntry" (id, S_ROOT_PATH, S_CLIENT_PATH, S_STORAGE_TYPE, S_USER, S_PASSWORD, S_HOST, I_PORT) VALUES (2, '/backups/projectfiles', NULL, 'ftp', 'me', '123456abcde', 'ftp.mysite.com', 21);
INSERT INTO "StorageEntry" (id, S_ROOT_PATH, S_CLIENT_PATH, S_STORAGE_TYPE, S_USER, S_PASSWORD, S_HOST, I_PORT) VALUES (3, '/backups/projectfiles', NULL, 'ftp', 'me', '123456abcde', 'ftp.othermysite.com', 21);

INSERT INTO "FileEntry" (id, S_FILEPATH, K_STORAGE_ID, S_USER, I_VERSION, T_CTIME, T_MTIME, T_ATIME, B_HAS_CONTENT) VALUES (1, '/path/to/a/video.mxf', 2, 'me', 1, '2017-01-17 16:55:00.123', '2017-01-17 16:55:00.123', '2017-01-17 16:55:00.123', false);
INSERT INTO "FileEntry" (id, S_FILEPATH, K_STORAGE_ID, S_USER, I_VERSION, T_CTIME, T_MTIME, T_ATIME, B_HAS_CONTENT) VALUES (2, '/path/to/a/file.project', 1, 'you', 1, '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', true);
INSERT INTO "FileEntry" (id, S_FILEPATH, K_STORAGE_ID, S_USER, I_VERSION, T_CTIME, T_MTIME, T_ATIME, B_HAS_CONTENT) VALUES (3, 'realfile', 1, 'you', 1, '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', true);
INSERT INTO "FileEntry" (id, S_FILEPATH, K_STORAGE_ID, S_USER, I_VERSION, T_CTIME, T_MTIME, T_ATIME, B_HAS_CONTENT) VALUES (4, 'testprojectfile', 1, 'you', 1, '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', false);
INSERT INTO "FileEntry" (id, S_FILEPATH, K_STORAGE_ID, S_USER, I_VERSION, T_CTIME, T_MTIME, T_ATIME, B_HAS_CONTENT) VALUES (5, '/path/to/thattestproject', 1, 'you', 1, '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', true);
INSERT INTO "FileEntry" (id, S_FILEPATH, K_STORAGE_ID, S_USER, I_VERSION, T_CTIME, T_MTIME, T_ATIME, B_HAS_CONTENT) VALUES (6, 'project_to_delete.prproj', 1, 'you', 1, '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', '2016-12-11 12:21:11.021', false);

INSERT INTO "PlutoProjectType" (id, S_NAME, U_UUID) VALUES (1, 'Premiere test', '330b4d84-ef24-41e2-b093-0d15829afa64');
INSERT INTO "PlutoProjectType" (id, S_NAME, U_UUID, K_PARENT) VALUES (2, 'Subtype test', '0145d384-f9ae-42af-9b2e-48adca11a7e4', 1);

INSERT INTO "ProjectType" (id, S_NAME, S_OPENS_WITH, S_TARGET_VERSION, S_FILE_EXTENSION, K_PLUTO_TYPE) VALUES (1, 'Premiere 2014 test', 'AdobePremierePro.app', '14.0', '.prproj', 1);
INSERT INTO "ProjectType" (id, S_NAME, S_OPENS_WITH, S_TARGET_VERSION, S_FILE_EXTENSION) VALUES (2, 'Prelude 2014 test', 'AdobePrelude.app', '14.0', '.plproj');
INSERT INTO "ProjectType" (id, S_NAME, S_OPENS_WITH, S_TARGET_VERSION, S_FILE_EXTENSION, K_PLUTO_TYPE) VALUES (3, 'Cubase test', 'Cubase.app', '6.0', '.cpr',2);
INSERT INTO "ProjectType" (id, S_NAME, S_OPENS_WITH, S_TARGET_VERSION, S_FILE_EXTENSION) VALUES (4, 'Aftereffects test', 'AdobeAfterEffects.app', '6.0', '.aep');


INSERT INTO "ProjectEntry" (id, K_PROJECT_TYPE, S_TITLE, T_CREATED,S_USER) VALUES (1, 1, 'InitialTestProject', '2016-12-11 12:21:11.021', 'me');
INSERT INTO "ProjectEntry" (id, K_PROJECT_TYPE, S_TITLE, S_VIDISPINE_ID,T_CREATED,S_USER) VALUES (2, 1, 'AnotherTestProject', 'VX-1234', '2016-12-11 12:21:11.021', 'you');
INSERT INTO "ProjectEntry" (id, K_PROJECT_TYPE, S_TITLE, S_VIDISPINE_ID,T_CREATED,S_USER) VALUES (3, 1, 'ThatTestProject', 'VX-2345', '2016-12-11 12:21:11.021', 'you');
INSERT INTO "ProjectEntry" (id, K_PROJECT_TYPE, S_TITLE, S_VIDISPINE_ID,T_CREATED,S_USER) VALUES (4, 1, 'WhoseTestProject', 'VX-2345', '2016-12-11 12:21:11.021', 'you');

INSERT INTO "ProjectFileAssociation" (id, K_PROJECT_ENTRY, K_FILE_ENTRY) VALUES (1, 1,2);
INSERT INTO "ProjectFileAssociation" (id, K_PROJECT_ENTRY, K_FILE_ENTRY) VALUES (2, 3,5);

INSERT INTO "ProjectTemplate" (id, S_NAME, K_PROJECT_TYPE, K_FILE_REF) VALUES (1, 'Premiere test template 1', 1,5);
INSERT INTO "ProjectTemplate" (id, S_NAME, K_PROJECT_TYPE, K_FILE_REF) VALUES (2, 'Another wonderful test template', 2, 2);
INSERT INTO "ProjectTemplate" (id, S_NAME, K_PROJECT_TYPE, K_FILE_REF) VALUES (3, 'Some random test template', 2, 2);

UPDATE "PlutoProjectType" SET K_DEFAULT_TEMPLATE=1 WHERE id=1;
UPDATE "PlutoProjectType" SET K_DEFAULT_TEMPLATE=3 WHERE id=2;

INSERT INTO "PostrunAction" (id, S_RUNNABLE, S_TITLE, S_OWNER, I_VERSION, T_CTIME) VALUES (1, 'FirstTestScript.py', 'First test postrun', 'system',1, '2018-01-01T12:13:24.000');
INSERT INTO "PostrunAction" (id, S_RUNNABLE, S_TITLE, S_OWNER, I_VERSION, T_CTIME) VALUES (2, 'SecondTestScript.py', 'Second test postrun', 'system',1, '2018-01-01T14:15:31.000');
INSERT INTO "PostrunAction" (id, S_RUNNABLE, S_TITLE, S_OWNER, I_VERSION, T_CTIME) VALUES (3, 'thirdTestScript.py', 'Third test postrun', 'system',1, '2018-01-01T14:15:31.000');
INSERT INTO "PostrunAction" (id, S_RUNNABLE, S_TITLE, S_OWNER, I_VERSION, T_CTIME) VALUES (4, 'fourthTestScript.py', 'Fourth test postrun', 'system',1, '2018-01-01T14:15:31.000');
INSERT INTO "PostrunAction" (id, S_RUNNABLE, S_TITLE, S_OWNER, I_VERSION, T_CTIME) VALUES (5, 'fifthTestScript.py', 'fifth test postrun', 'system',1, '2018-01-01T14:15:31.000');
INSERT INTO "PostrunAction" (id, S_RUNNABLE, S_TITLE, S_OWNER, I_VERSION, T_CTIME) VALUES (6, 'sixthTestScript.py', 'Sixth test postrun', 'system',1, '2018-01-01T14:15:31.000');

INSERT INTO "PostrunAssociationRow" (id, K_POSTRUN, K_PROJECTTYPE) VALUES (1, 1, 1);
INSERT INTO "PostrunAssociationRow" (id, K_POSTRUN, K_PROJECTTYPE) VALUES (2, 2, 1);
INSERT INTO "PostrunAssociationRow" (id, K_POSTRUN, K_PROJECTTYPE) VALUES (4, 5, 1);
INSERT INTO "PostrunAssociationRow" (id, K_POSTRUN, K_PROJECTTYPE) VALUES (3, 2, 4);

INSERT INTO "PostrunDependency" (id,K_SOURCE, K_DEPENDSON) VALUES (1, 1, 5);
INSERT INTO "PostrunDependency" (id,K_SOURCE, K_DEPENDSON) VALUES (2, 1, 6);
INSERT INTO "PostrunDependency" (id,K_SOURCE, K_DEPENDSON) VALUES (3, 4, 5);
INSERT INTO "PostrunDependency" (id,K_SOURCE, K_DEPENDSON) VALUES (4, 2, 1);

INSERT INTO "PlutoWorkingGroup" (id, S_HIDE, S_NAME, U_UUID) VALUES (1, NULL, 'Multimedia Social', '1b97c363-fba0-4771-9cb5-9bd65aaed306');
INSERT INTO "PlutoWorkingGroup" (id, S_HIDE, S_NAME, U_UUID) VALUES (2, 'hidden', 'Multimedia Anti-Social', 'DA60602E-55C1-4F2A-8EDD-2737BEB4916E');

INSERT INTO "PlutoCommission" (id, I_COLLECTION_ID, S_SITE_ID, T_CREATED, T_UPDATED, S_TITLE, S_STATUS, S_DESCRIPTION, K_WORKING_GROUP) VALUES (1, 1234, 'VX', '2018-01-01T12:13:24.000', '2018-01-01T12:13:24.000', 'My test commission', 'New', 'some very long description goes here', 1);
INSERT INTO "PlutoCommission" (id, I_COLLECTION_ID, S_SITE_ID, T_CREATED, T_UPDATED, S_TITLE, S_STATUS, S_DESCRIPTION, K_WORKING_GROUP) VALUES (2, 2345, 'VX', '2018-01-02T12:13:24.000', '2018-01-02T12:13:24.000', 'My test commission 2', 'In production', 'some very long description goes here', 1);
INSERT INTO "PlutoCommission" (id, I_COLLECTION_ID, S_SITE_ID, T_CREATED, T_UPDATED, S_TITLE, S_STATUS, S_DESCRIPTION, K_WORKING_GROUP) VALUES (3, 3456, 'VX', '2018-01-03T12:13:24.000', '2018-01-03T12:13:24.000', 'My test commission 3', 'Held', 'some very long description goes here', 1);
INSERT INTO "PlutoCommission" (id, I_COLLECTION_ID, S_SITE_ID, T_CREATED, T_UPDATED, S_TITLE, S_STATUS, S_DESCRIPTION, K_WORKING_GROUP) VALUES (4, 4567, 'VX', '2018-01-04T12:13:24.000', '2018-01-04T12:13:24.000', 'My test commission 4', 'Completed', 'some very long description goes here', 1);

INSERT INTO DEFAULTS (id, S_NAME, S_VALUE) VALUES (1, 'lunch', 'sandwich');
INSERT INTO DEFAULTS (id, S_NAME, S_VALUE) VALUES (2, 'breakfast', 'toast');
INSERT INTO DEFAULTS (id, S_NAME, S_VALUE) VALUES (3, 'dessert', 'nothing');
INSERT INTO DEFAULTS (id, S_NAME, S_VALUE) VALUES (4, 'project_storage_id', 1);

INSERT INTO "ProjectMetadata" (id, K_PROJECT_ENTRY, S_KEY, S_VALUE) VALUES (1, 2, 'first_key', 'first value');
INSERT INTO "ProjectMetadata" (id, K_PROJECT_ENTRY, S_KEY, S_VALUE) VALUES (2, 2, 'second_key', 'second value');

------------------------
SELECT pg_catalog.setval('"ProjectMetadata_id_seq"', 3, true);
SELECT pg_catalog.setval('"Defaults_id_seq"', 4, true);
SELECT pg_catalog.setval('"PlutoCommission_id_seq"', 5, true);
SELECT pg_catalog.setval('"PlutoWorkingGroup_id_seq"', 3, true);
SELECT pg_catalog.setval('"PostrunAction_id_seq"', 7, true);
SELECT pg_catalog.setval('"PostrunAssociationRow_id_seq"', 4, true);
SELECT pg_catalog.setval('"ProjectTemplate_id_seq"', 4, true);
SELECT pg_catalog.setval('"ProjectType_id_seq"', 4, true);
SELECT pg_catalog.setval('"StorageEntry_id_seq"', 3, true);
SELECT pg_catalog.setval('"ProjectFileAssociation_id_seq"', 3, false);
SELECT pg_catalog.setval('"ProjectEntry_id_seq"', 5, false);
SELECT pg_catalog.setval('"FileEntry_id_seq"', 6, true);

# --!Downs
delete from "ProjectMetadata";
delete from "PlutoWorkingGroup";
delete from "PlutoProjectType";
delete from DEFAULTS;
delete from "PlutoCommission";
delete from "PlutoWorkingGroup";
delete from "PostrunDependency";
delete from "PostrunAssociationRow";
delete from "PostrunAction";
delete from "ProjectFileAssociation";
delete from "FileEntry";
delete from "ProjectEntry";
delete from "ProjectTemplate";
delete from "ProjectType";
delete from "StorageEntry";
delete from "play_evolutions";