--
-- PostgreSQL database dump
--

-- Dumped from database version 9.6.19
-- Dumped by pg_dump version 9.6.19

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: Defaults_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."Defaults_id_seq"', 1, false);


--
-- Data for Name: StorageEntry; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."StorageEntry" (id, s_root_path, s_client_path, s_storage_type, s_user, s_password, s_host, i_port, e_status, s_device, b_versions, s_nickname) FROM stdin;
1	/tmp	/tmp	Local	\N	\N	\N	\N	ONLINE	\N	f	\N
\.


--
-- Data for Name: FileEntry; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."FileEntry" (id, s_filepath, k_storage_id, i_version, s_user, t_ctime, t_mtime, t_atime, b_has_content, b_has_link) FROM stdin;
1	blank_premiere_2017.prproj	1	1	72adedb0-f332-488c-b393-d8482b229d8a	2020-09-03 09:55:22.4+00	2020-09-03 09:55:22.4+00	2020-09-03 09:55:22.4+00	t	f
2	20200903_hgdhfh.prproj	1	1	system	2020-09-03 10:04:40.298919+00	2020-09-03 10:04:40.298919+00	2020-09-03 10:04:40.298919+00	t	f
\.


--
-- Name: FileEntry_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."FileEntry_id_seq"', 7, true);


--
-- Data for Name: PlutoWorkingGroup; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."PlutoWorkingGroup" (id, s_name, b_hide, s_commissioner, u_uuid) FROM stdin;
1	Multimedia Anti-Social	f	Boyd Paul	\N
\.


--
-- Data for Name: PlutoCommission; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."PlutoCommission" (id, i_collection_id, s_site_id, t_created, t_updated, s_title, s_status, s_description, k_working_group, s_original_commissioner, t_scheduled_completion, s_owner, s_production_office, s_notes, s_original_title) FROM stdin;
2	\N	\N	2020-09-11 11:20:53.527+00	2020-09-11 11:20:53.527+00	sdgsdfgsdg	New	\N	1	\N	2020-10-10 23:00:00+00	admin	UK	\N	\N
3	\N	\N	2020-09-11 11:26:13.847+00	2020-09-11 11:26:13.847+00	hghdfhfhfd	New	\N	1	\N	2020-10-10 23:00:00+00	admin	UK	\N	\N
4	\N	\N	2020-09-11 15:42:35.347+00	2020-09-11 15:42:35.347+00	dgsdgs	New	\N	1	\N	2020-10-10 23:00:00+00	testuser	UK	\N	\N
5	\N	\N	2020-09-11 15:43:27.43+00	2020-09-11 15:43:27.43+00	new commission	New	\N	1	\N	2020-10-10 23:00:00+00	testuser	UK	\N	\N
6	\N	\N	2020-09-14 10:37:10.419+00	2020-09-14 10:37:10.419+00	hdfhdfhd	New	\N	1	\N	2020-10-13 23:00:00+00	testuser	UK	\N	\N
7	\N	\N	2020-09-14 10:47:30.434+00	2020-09-14 10:47:30.434+00	kjhkghk	New	\N	1	\N	2020-10-13 23:00:00+00	testuser	UK	\N	\N
8	\N	\N	2020-09-14 11:41:33.237+00	2020-09-14 11:41:33.237+00	updated title	New	\N	1	\N	2020-10-13 23:00:00+00	testuser	UK	\N	\N
1	\N	\N	2020-09-03 10:01:56.444+00	2020-09-03 10:01:56.444+00	gsdfgsdgsdg	New	\N	1	\N	2020-10-02 23:00:00+00	testuser	UK	\N	\N
\.


--
-- Name: PlutoCommission_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."PlutoCommission_id_seq"', 8, true);


--
-- Name: PlutoWorkingGroup_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."PlutoWorkingGroup_id_seq"', 1, true);


--
-- Data for Name: PostrunAction; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."PostrunAction" (id, s_runnable, s_title, s_description, s_owner, i_version, t_ctime) FROM stdin;
\.


--
-- Name: PostrunAction_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."PostrunAction_id_seq"', 1, false);


--
-- Data for Name: ProjectType; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."ProjectType" (id, s_name, s_opens_with, s_target_version, s_file_extension) FROM stdin;
1	Premiere	Premiere	1.0	.prproj
\.


--
-- Data for Name: PostrunAssociationRow; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."PostrunAssociationRow" (id, k_projecttype, k_postrun) FROM stdin;
\.


--
-- Name: PostrunAssociationRow_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."PostrunAssociationRow_id_seq"', 1, false);


--
-- Data for Name: PostrunDependency; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."PostrunDependency" (id, k_source, k_dependson) FROM stdin;
\.


--
-- Name: PostrunDependency_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."PostrunDependency_id_seq"', 1, false);


--
-- Data for Name: ProjectEntry; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."ProjectEntry" (id, k_project_type, s_vidispine_id, s_title, t_created, s_user, k_working_group, k_commission, b_deletable, b_deeparchive, b_sensitive, s_status, s_production_office, t_updated) FROM stdin;
1	1	\N	hgdhfhjhgjgff	2020-09-03 10:04:40.519+00	72adedb0-f332-488c-b393-d8482b229d8a	1	1	t	f	f	New	UK	2020-09-14 11:49:37.393
\.


--
-- Name: ProjectEntry_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."ProjectEntry_id_seq"', 3, true);


--
-- Data for Name: ProjectFileAssociation; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."ProjectFileAssociation" (id, k_project_entry, k_file_entry) FROM stdin;
1	1	2
\.


--
-- Name: ProjectFileAssociation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."ProjectFileAssociation_id_seq"', 1, true);


--
-- Data for Name: ProjectMetadata; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."ProjectMetadata" (id, k_project_entry, s_key, s_value) FROM stdin;
\.


--
-- Name: ProjectMetadata_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."ProjectMetadata_id_seq"', 1, false);


--
-- Data for Name: ProjectTemplate; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public."ProjectTemplate" (id, s_name, k_project_type, k_file_ref, b_deprecated) FROM stdin;
1	Premiere	1	1	f
\.


--
-- Name: ProjectTemplate_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."ProjectTemplate_id_seq"', 1, true);


--
-- Name: ProjectType_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."ProjectType_id_seq"', 1, true);


--
-- Name: StorageEntry_id_seq; Type: SEQUENCE SET; Schema: public; Owner: projectlocker
--

SELECT pg_catalog.setval('public."StorageEntry_id_seq"', 1, true);


--
-- Data for Name: defaults; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public.defaults (id, s_name, s_value) FROM stdin;
\.


--
-- Data for Name: play_evolutions; Type: TABLE DATA; Schema: public; Owner: projectlocker
--

COPY public.play_evolutions (id, hash, applied_at, apply_script, revert_script, state, last_problem) FROM stdin;
1	64ff1959faf3b93942d3c1c9025c5b65d526dd66	2020-09-01 16:16:19.132	CREATE TABLE "StorageEntry" (\nid INTEGER NOT NULL PRIMARY KEY,\nS_ROOT_PATH CHARACTER VARYING,\nS_CLIENT_PATH CHARACTER VARYING,\nS_STORAGE_TYPE CHARACTER VARYING NOT NULL,\nS_USER CHARACTER VARYING,\nS_PASSWORD CHARACTER VARYING,\nS_HOST CHARACTER VARYING,\nI_PORT SMALLINT\n);\n\nCREATE SEQUENCE "StorageEntry_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "StorageEntry_id_seq" OWNED BY "StorageEntry".id;\n\nALTER TABLE public."StorageEntry_id_seq" OWNER TO projectlocker;\n\nALTER TABLE "StorageEntry" OWNER TO "projectlocker";\n\n--------------------------------------------------\n\nCREATE TABLE "ProjectType" (\nid INTEGER NOT NULL PRIMARY KEY,\nS_NAME CHARACTER VARYING NOT NULL,\nS_OPENS_WITH CHARACTER VARYING NOT NULL,\nS_TARGET_VERSION CHARACTER VARYING NOT NULL\n);\n\nCREATE SEQUENCE "ProjectType_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\n\nALTER SEQUENCE "ProjectType_id_seq" OWNED BY "ProjectType".id;\nALTER TABLE public."ProjectType_id_seq" OWNER TO projectlocker;\nALTER TABLE "ProjectType" OWNER TO "projectlocker";\n\n-------------------------------------------------\n\nCREATE TABLE "FileEntry" (\nid INTEGER NOT NULL PRIMARY KEY,\nS_FILEPATH CHARACTER VARYING NOT NULL,\nK_STORAGE_ID INTEGER NOT NULL,\nI_VERSION INTEGER NOT NULL,\nS_USER CHARACTER VARYING NOT NULL,\nT_CTIME TIMESTAMP WITH TIME ZONE NOT NULL,\nT_MTIME TIMESTAMP WITH TIME ZONE NOT NULL,\nT_ATIME TIMESTAMP WITH TIME ZONE NOT NULL,\nB_HAS_CONTENT BOOLEAN NOT NULL DEFAULT FALSE,\nB_HAS_LINK BOOLEAN NOT NULL DEFAULT FALSE\n);\n\nCREATE SEQUENCE "FileEntry_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\n\nALTER SEQUENCE "FileEntry_id_seq" OWNED BY "FileEntry".id;\n\nALTER TABLE public."FileEntry_id_seq" OWNER TO projectlocker;\n\nALTER TABLE "FileEntry" OWNER TO "projectlocker";\nCREATE UNIQUE INDEX IX_PATH_STORAGE ON "FileEntry" (S_FILEPATH, K_STORAGE_ID);\nALTER TABLE "FileEntry" ADD CONSTRAINT "fk_storage" FOREIGN KEY (K_STORAGE_ID) REFERENCES "StorageEntry"(id) DEFERRABLE INITIALLY DEFERRED;\n\n------------------------------------------------------\n\nCREATE TABLE "ProjectTemplate" (\nid INTEGER NOT NULL PRIMARY KEY,\nS_NAME CHARACTER VARYING NOT NULL,\nK_PROJECT_TYPE INTEGER NOT NULL,\nK_FILE_REF INTEGER NOT NULL\n);\nCREATE SEQUENCE "ProjectTemplate_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\n\nALTER SEQUENCE "ProjectTemplate_id_seq" OWNED BY "ProjectTemplate".id;\nALTER TABLE public."ProjectTemplate_id_seq" OWNER TO projectlocker;\n\nALTER TABLE "ProjectTemplate" OWNER TO "projectlocker";\nCREATE UNIQUE INDEX IX_TEMPLATE_NAME on "ProjectTemplate" (S_NAME);\nALTER TABLE "ProjectTemplate" ADD CONSTRAINT FK_PROJECT_TYPE FOREIGN KEY (K_PROJECT_TYPE) REFERENCES "ProjectType"(id) DEFERRABLE INITIALLY DEFERRED;\nALTER TABLE "ProjectTemplate" ADD CONSTRAINT FK_FILE_REF FOREIGN KEY (K_FILE_REF) REFERENCES "FileEntry"(id) DEFERRABLE INITIALLY DEFERRED;\n\n---------------------------------------------------\n\nCREATE TABLE "ProjectEntry" (\nid INTEGER NOT NULL PRIMARY KEY,\nK_PROJECT_TYPE INTEGER NOT NULL,\nS_VIDISPINE_ID CHARACTER VARYING NULL,\nS_TITLE CHARACTER VARYING NOT NULL,\nT_CREATED TIMESTAMP WITH TIME ZONE NOT NULL,\nS_USER CHARACTER VARYING NOT NULL\n);\n\nCREATE SEQUENCE "ProjectEntry_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER TABLE public."ProjectEntry_id_seq" OWNER TO projectlocker;\nALTER SEQUENCE "ProjectEntry_id_seq" OWNED BY "ProjectEntry".id;\n\nALTER TABLE "ProjectEntry" OWNER TO "projectlocker";\nALTER TABLE "ProjectEntry" ADD CONSTRAINT FK_PROJECT_TYPE FOREIGN KEY (K_PROJECT_TYPE) REFERENCES "ProjectType"(id) DEFERRABLE INITIALLY DEFERRED;\n\n----------------------------------------------------\n\nCREATE TABLE "ProjectFileAssociation" (\nid INTEGER NOT NULL  PRIMARY KEY,\nK_PROJECT_ENTRY INTEGER NOT NULL,\nK_FILE_ENTRY INTEGER NOT NULL\n);\nCREATE SEQUENCE "ProjectFileAssociation_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "ProjectFileAssociation_id_seq" OWNED BY "ProjectFileAssociation".id;\n\nALTER TABLE public."ProjectFileAssociation_id_seq" OWNER TO projectlocker;\n\nALTER TABLE "ProjectFileAssociation" OWNER TO "projectlocker";\nALTER TABLE "ProjectFileAssociation" ADD CONSTRAINT FK_PROJECT_ENTRY FOREIGN KEY (K_PROJECT_ENTRY) REFERENCES "ProjectEntry"(id) DEFERRABLE INITIALLY DEFERRED;\nALTER TABLE "ProjectFileAssociation" ADD CONSTRAINT FK_FILE_ENTRY FOREIGN KEY (K_FILE_ENTRY) REFERENCES "FileEntry"(id) DEFERRABLE INITIALLY DEFERRED;\n\n--------------------------------------------------\n\nALTER TABLE ONLY "FileEntry" ALTER COLUMN id SET DEFAULT nextval('"FileEntry_id_seq"'::regclass);\n\nALTER TABLE ONLY "ProjectEntry" ALTER COLUMN id SET DEFAULT nextval('"ProjectEntry_id_seq"'::regclass);\n\nALTER TABLE ONLY "ProjectFileAssociation" ALTER COLUMN id SET DEFAULT nextval('"ProjectFileAssociation_id_seq"'::regclass);\n\nALTER TABLE ONLY "ProjectTemplate" ALTER COLUMN id SET DEFAULT nextval('"ProjectTemplate_id_seq"'::regclass);\n\nALTER TABLE ONLY "ProjectType" ALTER COLUMN id SET DEFAULT nextval('"ProjectType_id_seq"'::regclass);\n\nALTER TABLE ONLY "StorageEntry" ALTER COLUMN id SET DEFAULT nextval('"StorageEntry_id_seq"'::regclass);\n\n---------------------------------------------------	DROP TABLE "ProjectFileAssociation" CASCADE;\nDROP TABLE "ProjectEntry" CASCADE;\nDROP TABLE "ProjectTemplate" CASCADE;\nDROP TABLE "FileEntry" CASCADE;\nDROP TABLE "ProjectType" CASCADE;\nDROP TABLE "StorageEntry" CASCADE;	applied	
2	3b8068582d73dc7d936d73a97b56f0981660c168	2020-09-01 16:16:19.759	ALTER TABLE "ProjectType" ADD COLUMN S_FILE_EXTENSION CHARACTER VARYING;	ALTER TABLE "ProjectType" DROP COLUMN S_FILE_EXTENSION;	applied	
3	1d7295a3d9cff4c0a024119c3856e864172f16ea	2020-09-01 16:16:19.783	CREATE TABLE "PostrunAction" (\nid INTEGER NOT NULL PRIMARY KEY,\nS_RUNNABLE CHARACTER VARYING NOT NULL,\nS_TITLE CHARACTER VARYING NOT NULL,\nS_DESCRIPTION CHARACTER VARYING NULL,\nS_OWNER CHARACTER VARYING NOT NULL,\nI_VERSION SMALLINT NOT NULL,\nT_CTIME TIMESTAMP WITH TIME ZONE\n);\n\nCREATE SEQUENCE "PostrunAction_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "PostrunAction_id_seq" OWNED BY "PostrunAction".id;\n\nALTER TABLE public."PostrunAction_id_seq" OWNER TO projectlocker;\nALTER TABLE ONLY "PostrunAction" ALTER COLUMN id SET DEFAULT nextval('"PostrunAction_id_seq"'::regclass);\n\nALTER TABLE "PostrunAction" OWNER TO "projectlocker";\n\nCREATE TABLE "PostrunAssociationRow" (\nid INTEGER NOT NULL PRIMARY KEY,\nK_PROJECTTYPE INTEGER NOT NULL,\nK_POSTRUN INTEGER NOT NULL\n);\n\nALTER TABLE "PostrunAssociationRow" ADD CONSTRAINT "FK_PROJECT_TYPE" FOREIGN KEY (K_PROJECTTYPE) REFERENCES "ProjectType"(id) DEFERRABLE INITIALLY DEFERRED;\nALTER TABLE "PostrunAssociationRow" ADD CONSTRAINT "FK_POSTRUN_ENTRY" FOREIGN KEY (K_POSTRUN) REFERENCES "PostrunAction"(id) DEFERRABLE INITIALLY DEFERRED;\n\nCREATE SEQUENCE "PostrunAssociationRow_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "PostrunAssociationRow_id_seq" OWNED BY "PostrunAssociationRow".id;\n\nALTER TABLE public."PostrunAssociationRow_id_seq" OWNER TO projectlocker;\n\nALTER TABLE "PostrunAssociationRow_id_seq" OWNER TO "projectlocker";\nALTER TABLE ONLY "PostrunAssociationRow" ALTER COLUMN id SET DEFAULT nextval('"PostrunAssociationRow_id_seq"'::regclass);	DROP TABLE "PostrunAssociationRow";\nDROP TABLE "PostrunAction";	applied	
4	f9d19fa9dfbd4dcc358d90f2f9a9533ad68ac440	2020-09-01 16:16:20.089	CREATE TABLE "PostrunDependency" (\nid INTEGER NOT NULL PRIMARY KEY,\nK_SOURCE INTEGER NOT NULL,\nK_DEPENDSON INTEGER NOT NULL,\nUNIQUE (K_SOURCE, K_DEPENDSON)\n);\n\nALTER TABLE "PostrunDependency" ADD CONSTRAINT "FK_SOURCE" FOREIGN KEY (K_SOURCE) REFERENCES "PostrunAction"(id) DEFERRABLE INITIALLY DEFERRED;\nALTER TABLE "PostrunDependency" ADD CONSTRAINT "FK_DEPENDS_ON" FOREIGN KEY (K_DEPENDSON) REFERENCES "PostrunAction"(id) DEFERRABLE INITIALLY DEFERRED;\n\nCREATE SEQUENCE "PostrunDependency_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "PostrunDependency_id_seq" OWNED BY "PostrunDependency".id;\n\nALTER TABLE public."PostrunDependency" OWNER TO projectlocker;\nALTER TABLE "PostrunDependency_id_seq" OWNER TO projectlocker;\n\nALTER TABLE ONLY "PostrunDependency" ALTER COLUMN id SET DEFAULT nextval('"PostrunDependency_id_seq"'::regclass);	DROP TABLE "PostrunDependency"	applied	
5	b74074c59901bae47914cd69a409e4ed3e3da6e2	2020-09-01 16:16:20.222	CREATE TABLE "PlutoWorkingGroup" (\nid INTEGER NOT NULL PRIMARY KEY,\nS_HIDE CHARACTER VARYING NULL,\nS_NAME CHARACTER VARYING NOT NULL,\nU_UUID CHARACTER VARYING NOT NULL UNIQUE\n);\n\nCREATE SEQUENCE "PlutoWorkingGroup_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "PlutoWorkingGroup_id_seq" OWNED BY "PlutoWorkingGroup".id;\n\nALTER TABLE public."PlutoWorkingGroup" OWNER TO projectlocker;\nALTER TABLE "PlutoWorkingGroup_id_seq" OWNER TO projectlocker;\n\nALTER TABLE ONLY "PlutoWorkingGroup" ALTER COLUMN id SET DEFAULT nextval('"PlutoWorkingGroup_id_seq"'::regclass);\n\nCREATE TABLE "PlutoCommission" (\nid INTEGER NOT NULL PRIMARY KEY,\nI_COLLECTION_ID INTEGER NOT NULL,\nS_SITE_ID CHARACTER VARYING NOT NULL,\nT_CREATED TIMESTAMP WITH TIME ZONE NOT NULL,\nT_UPDATED TIMESTAMP WITH TIME ZONE NOT NULL,\nS_TITLE CHARACTER VARYING NOT NULL,\nS_STATUS CHARACTER VARYING NOT NULL,\nS_DESCRIPTION CHARACTER VARYING NULL,\nK_WORKING_GROUP INTEGER NOT NULL,\nUNIQUE (S_SITE_ID, I_COLLECTION_ID)\n);\n\nCREATE INDEX IX_COLLECTION_ID ON "PlutoCommission" (I_COLLECTION_ID);\nCREATE INDEX IX_STATUS ON "PlutoCommission" (S_DESCRIPTION);\n\nALTER TABLE "PlutoCommission" ADD CONSTRAINT "fk_workinggroup" FOREIGN KEY (K_WORKING_GROUP) REFERENCES "PlutoWorkingGroup"(id) DEFERRABLE INITIALLY DEFERRED;\n\nCREATE SEQUENCE "PlutoCommission_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "PlutoCommission_id_seq" OWNED BY "PlutoCommission".id;\n\nALTER TABLE public."PlutoCommission" OWNER TO projectlocker;\nALTER TABLE "PlutoCommission_id_seq" OWNER TO projectlocker;\n\nALTER TABLE ONLY "PlutoCommission" ALTER COLUMN id SET DEFAULT nextval('"PlutoCommission_id_seq"'::regclass);\n\nALTER TABLE "ProjectEntry" ADD COLUMN K_WORKING_GROUP INTEGER NULL;\nALTER TABLE "ProjectEntry" ADD COLUMN K_COMMISSION INTEGER NULL;\nALTER TABLE "ProjectEntry" ADD CONSTRAINT FK_WORKING_GROUP FOREIGN KEY (K_WORKING_GROUP) REFERENCES "PlutoWorkingGroup"(id) DEFERRABLE INITIALLY DEFERRED;\nALTER TABLE "ProjectEntry" ADD CONSTRAINT FK_COMMISSION FOREIGN KEY (K_COMMISSION) REFERENCES "PlutoCommission"(id) DEFERRABLE INITIALLY DEFERRED;	ALTER TABLE "ProjectEntry" DROP CONSTRAINT FK_WORKING_GROUP;\nALTER TABLE "ProjectEntry" DROP CONSTRAINT FK_COMMISSION;\nALTER TABLE "ProjectEntry" DROP COLUMN K_WORKING_GROUP;\nALTER TABLE "ProjectEntry" DROP COLUMN K_COMMISSION;\nDROP TABLE "PlutoCommission" CASCADE ;\nDROP TABLE "PlutoWorkingGroup" CASCADE ;	applied	
16	685bb1499ffb5342d24a0c51523bf9b20df91958	2020-09-01 16:16:21.861	ALTER TABLE "ProjectEntry" ADD COLUMN S_STATUS VARCHAR(16) NOT NULL DEFAULT 'In Production';\nCREATE INDEX IX_PROJECT_STATUS ON "ProjectEntry" (S_STATUS);	DROP INDEX IX_PROJECT_STATUS;\nALTER TABLE "ProjectEntry" DROP COLUMN S_STATUS;	applied	
6	b379afed96008f15ab2daffe4b577633cebad107	2020-09-01 16:16:20.57	CREATE TABLE DEFAULTS (\nid INTEGER PRIMARY KEY NOT NULL,\ns_name CHARACTER VARYING UNIQUE NOT NULL,\ns_value CHARACTER VARYING NOT NULL\n);\n\n\nCREATE SEQUENCE "Defaults_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "Defaults_id_seq" OWNED BY DEFAULTS.id;\n\nALTER TABLE public.DEFAULTS OWNER TO projectlocker;\nALTER TABLE "Defaults_id_seq" OWNER TO projectlocker;\n\nALTER TABLE ONLY DEFAULTS ALTER COLUMN id SET DEFAULT nextval('"Defaults_id_seq"'::regclass);	DROP TABLE DEFAULTS;	applied	
7	2e1d25ba208552675d78d72ef69492680c67db5a	2020-09-01 16:16:20.701	ALTER TABLE "ProjectEntry" ADD COLUMN S_ADOBE_UUID CHARACTER VARYING NULL;\nALTER TABLE "ProjectType" ADD COLUMN S_PLUTO_TYPE CHARACTER VARYING NULL;\nALTER TABLE "ProjectType" ADD COLUMN S_PLUTO_SUBTYPE CHARACTER VARYING NULL;\nALTER TABLE "ProjectType" ADD CONSTRAINT C_UNIQUE_PLUTOID UNIQUE (S_PLUTO_TYPE, S_PLUTO_SUBTYPE);	ALTER TABLE "ProjectType" DROP CONSTRAINT C_UNIQUE_PLUTOID;\nALTER TABLE "ProjectEntry" DROP COLUMN S_ADOBE_UUID;\nALTER TABLE "ProjectType" DROP COLUMN S_PLUTO_TYPE;\nALTER TABLE "ProjectType" DROP COLUMN S_PLUTO_SUBTYPE	applied	
8	954c46226c1e4b598e811c0de5459d8235753764	2020-09-01 16:16:20.78	-- see https://stackoverflow.com/questions/8289100/create-unique-constraint-with-null-columns\nCREATE UNIQUE INDEX C_STORAGE_UNIQUEPATHS_NOHOST ON "StorageEntry" (S_ROOT_PATH, S_STORAGE_TYPE) WHERE S_HOST IS NULL;\nCREATE UNIQUE INDEX C_STORAGE_UNIQUEPATHS_WITHHOST ON "StorageEntry" (S_ROOT_PATH, S_STORAGE_TYPE, S_HOST) WHERE S_HOST IS NOT NULL;	DROP INDEX C_STORAGE_UNIQUEPATHS_WITHHOST;\nDROP INDEX C_STORAGE_UNIQUEPATHS_NOHOST;	applied	
9	9dd92989c776d6f026be4d158ec5eac5b19b569c	2020-09-01 16:16:20.951	CREATE TABLE "PlutoProjectType" (\nid INTEGER PRIMARY KEY,\nS_NAME CHARACTER VARYING NOT NULL,\nU_UUID CHARACTER VARYING UNIQUE NOT NULL,\nK_PARENT INTEGER NULL\n);\nCREATE SEQUENCE "PlutoProjectType_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "PlutoProjectType_id_seq" OWNED BY "PlutoProjectType".id;\nALTER TABLE ONLY "PlutoProjectType" ALTER COLUMN id SET DEFAULT nextval('"PlutoProjectType_id_seq"'::regclass);\nALTER TABLE public."PlutoProjectType_id_seq" OWNER TO projectlocker;\n\nALTER TABLE "PlutoProjectType" OWNER TO "projectlocker";\n\nCREATE INDEX IX_PROJECTTYPE_NAME on "PlutoProjectType" (S_NAME);\nALTER TABLE "PlutoProjectType" ADD CONSTRAINT FK_PLUTOTYPEPARENT FOREIGN KEY (K_PARENT)  REFERENCES "PlutoProjectType"(id) DEFERRABLE INITIALLY DEFERRED;\n\nALTER TABLE "ProjectType" DROP COLUMN S_PLUTO_TYPE;\nALTER TABLE "ProjectType" DROP COLUMN S_PLUTO_SUBTYPE;\nALTER TABLE "ProjectType" ADD COLUMN K_PLUTO_TYPE INTEGER NULL;\nALTER TABLE "ProjectTemplate" ADD COLUMN K_PLUTO_SUBTYPE INTEGER NULL;\nALTER TABLE "ProjectType" ADD CONSTRAINT FK_PLUTO_TYPE FOREIGN KEY (K_PLUTO_TYPE) REFERENCES "PlutoProjectType"(id);\nALTER TABLE "ProjectTemplate" ADD CONSTRAINT FK_PLUTO_SUBTYPE FOREIGN KEY (K_PLUTO_SUBTYPE) REFERENCES "PlutoProjectType"(id);	ALTER TABLE "ProjectType" DROP CONSTRAINT FK_PLUTO_TYPE;\nALTER TABLE "ProjectTemplate" DROP CONSTRAINT FK_PLUTO_SUBTYPE;\nALTER TABLE "ProjectType" DROP COLUMN K_PLUTO_TYPE;\nALTER TABLE "ProjectTemplate" DROP COLUMN K_PLUTO_SUBTYPE;\nALTER TABLE "ProjectType" ADD COLUMN S_PLUTO_TYPE CHARACTER VARYING NULL;\nALTER TABLE "ProjectType" ADD COLUMN S_PLUTO_SUBTYPE CHARACTER VARYING NULL;\nDROP TABLE "PlutoProjectType";	applied	
10	26daa5abcb33826314acb3424372fb86faefdc19	2020-09-01 16:16:21.203	ALTER TABLE "PlutoProjectType" ADD COLUMN K_DEFAULT_TEMPLATE INTEGER NULL ;\nALTER TABLE "PlutoProjectType" ADD CONSTRAINT FK_DEFAULT_TEMPLATE FOREIGN KEY (K_DEFAULT_TEMPLATE) REFERENCES "ProjectTemplate"(id);	ALTER TABLE "PlutoProjectType" DROP CONSTRAINT FK_DEFAULT_TEMPLATE;\nALTER TABLE "PlutoProjectType" DROP COLUMN K_DEFAULT_TEMPLATE;	applied	
11	e533e17bac5414b4b097dd47ad9b8317bbb20373	2020-09-01 16:16:21.247	CREATE TABLE "ProjectMetadata" (\nid INTEGER NOT NULL PRIMARY KEY,\nK_PROJECT_ENTRY INTEGER NOT NULL,\nS_KEY CHARACTER VARYING NOT NULL,\nS_VALUE CHARACTER VARYING\n);\n\nALTER TABLE "ProjectMetadata" ADD CONSTRAINT "FK_PROJECT_ENTRY" FOREIGN KEY (K_PROJECT_ENTRY) REFERENCES "ProjectEntry"(id);\nCREATE UNIQUE INDEX "IX_PROJECT_ENTRY_METAKEY" ON "ProjectMetadata"(K_PROJECT_ENTRY,S_KEY);\nALTER TABLE "ProjectEntry" DROP COLUMN S_ADOBE_UUID;\n\nCREATE SEQUENCE "ProjectMetadata_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "ProjectMetadata_id_seq" OWNED BY "ProjectMetadata".id;\nALTER TABLE ONLY "ProjectMetadata" ALTER COLUMN id SET DEFAULT nextval('"ProjectMetadata_id_seq"'::regclass);\nALTER TABLE public."ProjectMetadata_id_seq" OWNER TO projectlocker;	DROP TABLE "ProjectMetadata";\nALTER TABLE "ProjectEntry" ADD COLUMN S_ADOBE_UUID CHARACTER VARYING NULL;	applied	
12	4ab0be66065da6d128ed5cf767e6d8e466b05473	2020-09-01 16:16:21.416	ALTER TABLE "StorageEntry" ADD COLUMN E_STATUS CHARACTER VARYING NULL DEFAULT 'UNKNOWN';	ALTER TABLE "StorageEntry" DROP COLUMN E_STATUS;	applied	
13	569a1ce1b9d81fedbb61865636ac7b2f77898050	2020-09-01 16:16:21.542	ALTER TABLE "ProjectEntry" ADD COLUMN B_DELETABLE boolean null;\nALTER TABLE "ProjectEntry" ADD COLUMN B_DEEPARCHIVE boolean null;\nALTER TABLE "ProjectEntry" ADD COLUMN B_SENSITIVE boolean null;	ALTER TABLE "ProjectEntry" DROP COLUMN B_DELETABLE;\nALTER TABLE "ProjectEntry" DROP COLUMN B_DEEPARCHIVE;\nALTER TABLE "ProjectEntry" DROP COLUMN B_SENSITIVE;	applied	
14	7df16c75849e3d7a0af7e76e0c1f502966ed3378	2020-09-01 16:16:21.593	ALTER TABLE "ProjectTemplate" ADD COLUMN B_DEPRECATED boolean null;	ALTER TABLE "ProjectTemplate" DROP COLUMN B_DEPRECATED;	applied	
15	3427f9d246df1d29a072b6149ab99517a7c05d44	2020-09-01 16:16:21.623	ALTER TABLE "StorageEntry" ADD COLUMN S_DEVICE VARCHAR(128) NULL;\nALTER TABLE "StorageEntry" ADD COLUMN B_VERSIONS BOOLEAN DEFAULT FALSE;\nALTER TABLE "StorageEntry" ADD COLUMN S_NICKNAME VARCHAR(128) NULL;\nDROP INDEX IX_PATH_STORAGE;\nCREATE UNIQUE INDEX IX_PATHVERS_STORAGE ON "FileEntry" (S_FILEPATH, K_STORAGE_ID, I_VERSION);\nDROP INDEX C_STORAGE_UNIQUEPATHS_WITHHOST;\nCREATE UNIQUE INDEX C_STORAGE_UNIQUEPATHS_WITHHOST ON "StorageEntry" (S_ROOT_PATH, S_DEVICE, S_STORAGE_TYPE, S_HOST) WHERE S_HOST IS NOT NULL;	ALTER TABLE "StorageEntry" DROP COLUMN S_DEVICE;\nALTER TABLE "StorageEntry" DROP COLUMN B_VERSIONS;\nALTER TABLE "StorageEntry" DROP COLUMN S_NICKNAME;\nDROP INDEX IX_PATHVERS_STORAGE;\nCREATE UNIQUE INDEX IX_PATH_STORAGE ON "FileEntry" (S_FILEPATH, K_STORAGE_ID);\nDROP INDEX C_STORAGE_UNIQUEPATHS_WITHHOST;\nCREATE UNIQUE INDEX C_STORAGE_UNIQUEPATHS_WITHHOST ON "StorageEntry" (S_ROOT_PATH, S_STORAGE_TYPE, S_HOST) WHERE S_HOST IS NOT NULL;	applied	
17	b27af078da23f7744ab4ccbf662696e50780dd19	2020-09-01 16:16:21.998	ALTER TABLE "PlutoCommission" ADD COLUMN S_ORIGINAL_COMMISSIONER VARCHAR(512) NULL;\nALTER TABLE "PlutoCommission" ADD COLUMN T_SCHEDULED_COMPLETION TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT '2020-01-01T01:00:00Z';\nALTER TABLE "PlutoCommission" ADD COLUMN S_OWNER VARCHAR(512) NOT NULL DEFAULT 'LegacyProject';\nALTER TABLE "PlutoCommission" ADD COLUMN S_PRODUCTION_OFFICE VARCHAR(8) NOT NULL DEFAULT 'UK';\nALTER TABLE "PlutoCommission" ADD COLUMN S_NOTES TEXT NULL;\nALTER TABLE "PlutoCommission" ADD COLUMN S_ORIGINAL_TITLE TEXT NULL;\nALTER TABLE "PlutoCommission" ALTER COLUMN I_COLLECTION_ID DROP NOT NULL;\nALTER TABLE "PlutoCommission" ALTER COLUMN S_SITE_ID DROP NOT NULL;\nALTER TABLE "PlutoWorkingGroup" DROP COLUMN U_UUID;\nALTER TABLE "PlutoWorkingGroup" DROP COLUMN S_HIDE;\nALTER TABLE "PlutoWorkingGroup" ADD COLUMN B_HIDE BOOLEAN NOT NULL DEFAULT FALSE;\nALTER TABLE "PlutoWorkingGroup" ADD COLUMN S_COMMISSIONER VARCHAR(512) NOT NULL DEFAULT 'LegacyProject';\nCREATE INDEX IX_COMMISSION_OWNER ON "PlutoCommission" (S_OWNER);	DROP INDEX IX_COMMISSION_OWNER;\nALTER TABLE "PlutoCommission" DROP COLUMN S_ORIGINAL_TITLE;\nALTER TABLE "PlutoCommission" DROP COLUMN S_NOTES;\nALTER TABLE "PlutoCommission" DROP COLUMN S_PRODUCTION_OFFICE;\nALTER TABLE "PlutoCommission" DROP COLUMN S_OWNER;\nALTER TABLE "PlutoCommission" DROP COLUMN T_SCHEDULED_COMPLETION;\nALTER TABLE "PlutoCommission" DROP COLUMN S_ORIGINAL_COMMISSIONER;\nALTER TABLE "PlutoCommission" ALTER COLUMN I_COLLECTION_ID SET NOT NULL;\nALTER TABLE "PlutoCommission" ALTER COLUMN S_SITE_ID SET NOT NULL;\nALTER TABLE "PlutoWorkingGroup" DROP COLUMN S_COMMISSIONER ;\nALTER TABLE "PlutoWorkingGroup" ADD COLUMN U_UUID CHARACTER VARYING NOT NULL DEFAULT '63cab052-0b60-4ebd-86c0-f8ef1bd8c574';\nALTER TABLE "PlutoWorkingGroup" DROP COLUMN B_HIDE;\nALTER TABLE "PlutoWorkingGroup" ADD COLUMN S_HIDE CHARACTER VARYING NULL;	applied	
18	6bb54aa48380d530f4b0634e3e489d391097f73b	2020-09-01 16:16:22.657	ALTER TABLE "ProjectEntry" ADD COLUMN S_PRODUCTION_OFFICE VARCHAR(8) NOT NULL DEFAULT 'UK';\nCREATE INDEX IX_PROJECT_PRODUCTION_OFFICE ON "ProjectEntry" (S_PRODUCTION_OFFICE);	DROP INDEX IX_PROJECT_PRODUCTION_OFFICE;\nALTER TABLE "ProjectEntry" DROP COLUMN S_PRODUCTION_OFFICE;	applied	
20	b39537997bf9b94edc330d4c547005ce8f6681bc	2020-10-06 10:11:45.698	ALTER TABLE "ProjectType" DROP CONSTRAINT FK_PLUTO_TYPE;\nALTER TABLE "ProjectTemplate" DROP CONSTRAINT FK_PLUTO_SUBTYPE;\nALTER TABLE "ProjectType" DROP COLUMN K_PLUTO_TYPE;\nALTER TABLE "ProjectTemplate" DROP COLUMN K_PLUTO_SUBTYPE;\nDROP TABLE "PlutoProjectType";	CREATE TABLE "PlutoProjectType" (\nid INTEGER PRIMARY KEY,\nS_NAME CHARACTER VARYING NOT NULL,\nU_UUID CHARACTER VARYING UNIQUE NOT NULL,\nK_PARENT INTEGER NULL\n);\nCREATE SEQUENCE "PlutoProjectType_id_seq"\nSTART WITH 1\nINCREMENT BY 1\nNO MINVALUE\nNO MAXVALUE\nCACHE 1;\nALTER SEQUENCE "PlutoProjectType_id_seq" OWNED BY "PlutoProjectType".id;\nALTER TABLE ONLY "PlutoProjectType" ALTER COLUMN id SET DEFAULT nextval('"PlutoProjectType_id_seq"'::regclass);\nALTER TABLE public."PlutoProjectType_id_seq" OWNER TO projectlocker;\n\nALTER TABLE "PlutoProjectType" OWNER TO "projectlocker";\n\nALTER TABLE "PlutoProjectType" ADD COLUMN K_DEFAULT_TEMPLATE INTEGER NULL ;\nALTER TABLE "PlutoProjectType" ADD CONSTRAINT FK_DEFAULT_TEMPLATE FOREIGN KEY (K_DEFAULT_TEMPLATE) REFERENCES "ProjectTemplate"(id);\n\nALTER TABLE "ProjectType" ADD COLUMN K_PLUTO_TYPE INTEGER NULL;\nALTER TABLE "ProjectTemplate" ADD COLUMN K_PLUTO_SUBTYPE INTEGER NULL;\nALTER TABLE "ProjectType" ADD CONSTRAINT FK_PLUTO_TYPE FOREIGN KEY (K_PLUTO_TYPE) REFERENCES "PlutoProjectType"(id);\nALTER TABLE "ProjectTemplate" ADD CONSTRAINT FK_PLUTO_SUBTYPE FOREIGN KEY (K_PLUTO_SUBTYPE) REFERENCES "PlutoProjectType"(id);	applied	
19	d6defa89dd54e950271faf0cda47e6121205b931	2020-09-14 11:49:37.384	ALTER TABLE "ProjectEntry" ADD COLUMN T_UPDATED TIMESTAMP NOT NULL DEFAULT NOW();	ALTER TABLE "ProjectEntry" DROP COLUMN T_UPDATED;	applied	
\.


--
-- PostgreSQL database dump complete
--

