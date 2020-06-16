# --!Ups
ALTER TABLE "PlutoCommission" ADD COLUMN S_ORIGINAL_COMMISSIONER VARCHAR(512) NULL;
ALTER TABLE "PlutoCommission" ADD COLUMN T_SCHEDULED_COMPLETION TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT '2020-01-01T01:00:00Z';
ALTER TABLE "PlutoCommission" ADD COLUMN S_OWNER VARCHAR(512) NOT NULL DEFAULT 'LegacyProject';
ALTER TABLE "PlutoCommission" ADD COLUMN S_PRODUCTION_OFFICE VARCHAR(8) NOT NULL DEFAULT 'UK';
ALTER TABLE "PlutoCommission" ADD COLUMN S_NOTES TEXT NULL;
ALTER TABLE "PlutoCommission" ADD COLUMN S_ORIGINAL_TITLE TEXT NULL;
ALTER TABLE "PlutoCommission" ALTER COLUMN I_COLLECTION_ID DROP NOT NULL;
ALTER TABLE "PlutoCommission" ALTER COLUMN S_SITE_ID DROP NOT NULL;
CREATE INDEX IX_COMMISSION_OWNER ON "PlutoCommission" (S_OWNER);

# --!Downs
DROP INDEX IX_COMMISSION_OWNER;
ALTER TABLE "PlutoCommission" DROP COLUMN S_ORIGINAL_TITLE;
ALTER TABLE "PlutoCommission" DROP COLUMN S_NOTES;
ALTER TABLE "PlutoCommission" DROP COLUMN S_PRODUCTION_OFFICE;
ALTER TABLE "PlutoCommission" DROP COLUMN S_OWNER;
ALTER TABLE "PlutoCommission" DROP COLUMN T_SCHEDULED_COMPLETION;
ALTER TABLE "PlutoCommission" DROP COLUMN S_ORIGINAL_COMMISSIONER;
ALTER TABLE "PlutoCommission" ALTER COLUMN I_COLLECTION_ID SET NOT NULL;
ALTER TABLE "PlutoCommission" ALTER COLUMN S_SITE_ID SET NOT NULL;