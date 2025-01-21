# --!Ups
CREATE TABLE "DeletionRecord" (
     id INTEGER NOT NULL PRIMARY KEY,
     K_PROJECT_ENTRY INTEGER NOT NULL,
     S_USER CHARACTER VARYING NOT NULL,
     T_DELETED TIMESTAMP WITH TIME ZONE NOT NULL,
     T_CREATED TIMESTAMP WITH TIME ZONE NOT NULL,
     S_WORKING_GROUP CHARACTER VARYING NOT NULL,
     UNIQUE (K_PROJECT_ENTRY)
);

CREATE SEQUENCE "DeletionRecord_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE "DeletionRecord_id_seq" OWNED BY "DeletionRecord".id;

ALTER TABLE public."DeletionRecord_id_seq" OWNER TO projectlocker;

ALTER TABLE "DeletionRecord" OWNER TO "projectlocker";

ALTER TABLE ONLY "DeletionRecord" ALTER COLUMN id SET DEFAULT nextval('"DeletionRecord_id_seq"'::regclass);

# --!Downs
DROP TABLE "DeletionRecord" CASCADE;
