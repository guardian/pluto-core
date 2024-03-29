# -- !Ups
CREATE TABLE "AssetFolderFileEntry" (
    id INTEGER NOT NULL PRIMARY KEY,
    S_FILEPATH CHARACTER VARYING NOT NULL,
    K_STORAGE_ID INTEGER NOT NULL,
    I_VERSION INTEGER NOT NULL,
    T_CTIME TIMESTAMP WITH TIME ZONE NOT NULL,
    T_MTIME TIMESTAMP WITH TIME ZONE NOT NULL,
    T_ATIME TIMESTAMP WITH TIME ZONE NOT NULL,
    I_PROJECT INTEGER NULL,
    I_BACKUP_OF INTEGER NULL
);

CREATE SEQUENCE "AssetFolderFileEntry_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE "AssetFolderFileEntry_id_seq" OWNED BY "AssetFolderFileEntry".id;

ALTER TABLE public."AssetFolderFileEntry_id_seq" OWNER TO projectlocker;

ALTER TABLE "AssetFolderFileEntry" OWNER TO "projectlocker";

ALTER TABLE "AssetFolderFileEntry" ADD CONSTRAINT "fk_storage" FOREIGN KEY (K_STORAGE_ID) REFERENCES "StorageEntry"(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY "AssetFolderFileEntry" ALTER COLUMN id SET DEFAULT nextval('"AssetFolderFileEntry_id_seq"'::regclass);

# -- !Downs
DROP TABLE "AssetFolderFileEntry" CASCADE;
