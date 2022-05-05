# --!Ups
ALTER TABLE "ProjectEntry" ADD COLUMN s_is_obit_project VARCHAR NULL;

# --!Downs
ALTER TABLE "ProjectEntry" DROP COLUMN s_is_obit_project;
