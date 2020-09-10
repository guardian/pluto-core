# --!Ups
ALTER TABLE "ProjectType" DROP CONSTRAINT FK_PLUTO_TYPE;
ALTER TABLE "ProjectTemplate" DROP CONSTRAINT FK_PLUTO_SUBTYPE;
ALTER TABLE "ProjectType" DROP COLUMN K_PLUTO_TYPE;
ALTER TABLE "ProjectTemplate" DROP COLUMN K_PLUTO_SUBTYPE;
DROP TABLE "PlutoProjectType";

# --!Downs
CREATE TABLE "PlutoProjectType" (
                                    id INTEGER PRIMARY KEY,
                                    S_NAME CHARACTER VARYING NOT NULL,
                                    U_UUID CHARACTER VARYING UNIQUE NOT NULL,
                                    K_PARENT INTEGER NULL
);
CREATE SEQUENCE "PlutoProjectType_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE "PlutoProjectType_id_seq" OWNED BY "PlutoProjectType".id;
ALTER TABLE ONLY "PlutoProjectType" ALTER COLUMN id SET DEFAULT nextval('"PlutoProjectType_id_seq"'::regclass);
ALTER TABLE public."PlutoProjectType_id_seq" OWNER TO projectlocker;

ALTER TABLE "PlutoProjectType" OWNER TO "projectlocker";

ALTER TABLE "PlutoProjectType" ADD COLUMN K_DEFAULT_TEMPLATE INTEGER NULL ;
ALTER TABLE "PlutoProjectType" ADD CONSTRAINT FK_DEFAULT_TEMPLATE FOREIGN KEY (K_DEFAULT_TEMPLATE) REFERENCES "ProjectTemplate"(id);

ALTER TABLE "ProjectType" ADD COLUMN K_PLUTO_TYPE INTEGER NULL;
ALTER TABLE "ProjectTemplate" ADD COLUMN K_PLUTO_SUBTYPE INTEGER NULL;
ALTER TABLE "ProjectType" ADD CONSTRAINT FK_PLUTO_TYPE FOREIGN KEY (K_PLUTO_TYPE) REFERENCES "PlutoProjectType"(id);
ALTER TABLE "ProjectTemplate" ADD CONSTRAINT FK_PLUTO_SUBTYPE FOREIGN KEY (K_PLUTO_SUBTYPE) REFERENCES "PlutoProjectType"(id);