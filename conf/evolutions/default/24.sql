# --!Ups
CREATE TABLE "ValidationJob" (id INTEGER NOT NULL PRIMARY KEY, u_uuid UUID, s_username character varying not null, s_jobtype character varying not null, t_started_at TIMESTAMP WITH TIME ZONE, t_completed_at TIMESTAMP WITH TIME ZONE, s_status CHARACTER VARYING NOT NULL, s_error_message CHARACTER VARYING);
CREATE UNIQUE INDEX validationjob_uuid ON "ValidationJob" (U_UUID);
CREATE INDEX validationjob_status ON "ValidationJob" (S_STATUS);

CREATE SEQUENCE "ValidationJob_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE "ValidationJob_id_seq" OWNED BY "ValidationJob".id;

ALTER TABLE ONLY "ValidationJob" ALTER COLUMN id SET DEFAULT nextval('"ValidationJob_id_seq"'::regclass);

CREATE TABLE "ValidationProblem" (pk INTEGER NOT NULL PRIMARY KEY, u_job_id UUID, t_timestamp TIMESTAMP WITH TIME ZONE NOT NULL, s_entity_class CHARACTER VARYING NOT NULL, i_entity_id INT NOT NULL, s_notes CHARACTER VARYING);
ALTER TABLE "ValidationProblem" ADD CONSTRAINT "fk_job_id" FOREIGN KEY (u_job_id) REFERENCES "ValidationJob"(u_uuid) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX entity_id_idx ON "ValidationProblem" (i_entity_id);
CREATE SEQUENCE "ValidationProblem_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE "ValidationProblem_id_seq" OWNED BY "ValidationJob".id;

ALTER TABLE ONLY "ValidationProblem" ALTER COLUMN pk SET DEFAULT nextval('"ValidationProblem_id_seq"'::regclass);

# --!Downs
DROP TABLE "ValidationJob";
DROP TABLE "ValidationProblem";