#!/usr/bin/env bash

#This script should be run as the postgres user
createuser projectlocker
createuser projectlocker_test

psql << EOF
alter user projectlocker password 'projectlocker';
alter user projectlocker_test password 'projectlocker';
EOF

createdb projectlocker -O projectlocker
createdb projectlocker_test -O projectlocker
createdb journal -O projectlocker

# taken from https://github.com/akka/akka-persistence-jdbc/blob/v3.5.2/src/test/resources/schema/postgres/postgres-schema.sql. Will need updating if
# akka-jdbc-persistence is upgraded.
psql journal << EOF
DROP TABLE IF EXISTS public.journal;

CREATE TABLE IF NOT EXISTS public.journal (
  ordering BIGSERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE,
  tags VARCHAR(255) DEFAULT NULL,
  message BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX journal_ordering_idx ON public.journal(ordering);

DROP TABLE IF EXISTS public.snapshot;

CREATE TABLE IF NOT EXISTS public.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);
EOF
