# --!Ups
ALTER TABLE "PlutoCommission" ADD COLUMN s_google_folder varchar(255);

# --!Downs
ALTER TABLE "PlutoCommission" DROP COLUMN s_google_folder;