# -- !Ups
CREATE TABLE "MissingAssetFileEntry" (
  id INTEGER NOT NULL PRIMARY KEY,
  K_PROJECT_ID INTEGER NOT NULL,
  S_FILEPATH CHARACTER VARYING NOT NULL
);

CREATE SEQUENCE "MissingAssetFileEntry_id_seq"
START WITH 1
INCREMENT BY 1
NO MINVALUE
NO MAXVALUE
CACHE 1;

ALTER SEQUENCE "MissingAssetFileEntry_id_seq" OWNED BY "MissingAssetFileEntry".id;

ALTER TABLE public."MissingAssetFileEntry_id_seq" OWNER TO projectlocker;

ALTER TABLE "MissingAssetFileEntry" OWNER TO "projectlocker";

ALTER TABLE "MissingAssetFileEntry" ADD CONSTRAINT "fk_project" FOREIGN KEY (K_PROJECT_ID) REFERENCES "ProjectEntry"(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY "MissingAssetFileEntry" ALTER COLUMN id SET DEFAULT nextval('"MissingAssetFileEntry_id_seq"'::regclass);

# -- !Downs
DROP TABLE "MissingAssetFileEntry" CASCADE;
