# --!Ups
CREATE TABLE "MatrixDeleteJob" (
     id INTEGER NOT NULL PRIMARY KEY,
     K_PROJECT_ENTRY INTEGER NOT NULL,
     S_STATUS CHARACTER VARYING NOT NULL,
     UNIQUE (K_PROJECT_ENTRY)
);

CREATE SEQUENCE "MatrixDeleteJob_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE "MatrixDeleteJob_id_seq" OWNED BY "DeleteJob".id;

ALTER TABLE public."MatrixDeleteJob_id_seq" OWNER TO projectlocker;

ALTER TABLE "MatrixDeleteJob" OWNER TO "projectlocker";

CREATE TABLE "MatrixDeleteData" (
     id INTEGER NOT NULL PRIMARY KEY,
     K_PROJECT_ENTRY INTEGER NOT NULL,
     S_ITEM CHARACTER VARYING NOT NULL
);

CREATE SEQUENCE "MatrixDeleteData_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE "MatrixDeleteData_id_seq" OWNED BY "ItemDeleteData".id;

ALTER TABLE public."MatrixDeleteData_id_seq" OWNER TO projectlocker;

ALTER TABLE "MatrixDeleteData" OWNER TO "projectlocker";

ALTER TABLE ONLY "MatrixDeleteJob" ALTER COLUMN id SET DEFAULT nextval('"DeleteJob_id_seq"'::regclass);

ALTER TABLE ONLY "MatrixDeleteData" ALTER COLUMN id SET DEFAULT nextval('"ItemDeleteData_id_seq"'::regclass);

# --!Downs
DROP TABLE "MatrixDeleteJob" CASCADE;
DROP TABLE "MatrixDeleteData" CASCADE;
