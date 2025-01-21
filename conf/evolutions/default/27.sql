# --!Ups
CREATE TABLE "DeleteJob" (
     id INTEGER NOT NULL PRIMARY KEY,
     K_PROJECT_ENTRY INTEGER NOT NULL,
     S_STATUS CHARACTER VARYING NOT NULL,
     UNIQUE (K_PROJECT_ENTRY)
);

CREATE SEQUENCE "DeleteJob_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE "DeleteJob_id_seq" OWNED BY "DeleteJob".id;

ALTER TABLE public."DeleteJob_id_seq" OWNER TO projectlocker;

ALTER TABLE "DeleteJob" OWNER TO "projectlocker";

CREATE TABLE "ItemDeleteData" (
     id INTEGER NOT NULL PRIMARY KEY,
     K_PROJECT_ENTRY INTEGER NOT NULL,
     S_ITEM CHARACTER VARYING NOT NULL
);

CREATE SEQUENCE "ItemDeleteData_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE "ItemDeleteData_id_seq" OWNED BY "ItemDeleteData".id;

ALTER TABLE public."ItemDeleteData_id_seq" OWNER TO projectlocker;

ALTER TABLE "ItemDeleteData" OWNER TO "projectlocker";

ALTER TABLE ONLY "DeleteJob" ALTER COLUMN id SET DEFAULT nextval('"DeleteJob_id_seq"'::regclass);

ALTER TABLE ONLY "ItemDeleteData" ALTER COLUMN id SET DEFAULT nextval('"ItemDeleteData_id_seq"'::regclass);

# --!Downs
DROP TABLE "DeleteJob" CASCADE;
DROP TABLE "ItemDeleteData" CASCADE;
