# --!Ups
CREATE TABLE "ValidationJob" (id INTEGER NOT NULL PRIMARY KEY, u_uuid UUID, s_username character varying not null, t_started_at TIMESTAMP WITH TIME ZONE, t_completed_at TIMESTAMP WITH TIME ZONE, s_status CHARACTER VARYING NOT NULL, s_error_message CHARACTER VARYING);
CREATE UNIQUE INDEX validationjob_uuid ON "ValidationJob" (U_UUID);
CREATE INDEX validationjob_status ON "ValidationJob" (S_STATUS);

# --!Downs
DROP TABLE "ValidationJob";