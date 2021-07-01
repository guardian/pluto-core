# -- !Ups
ALTER TABLE "StorageEntry" ADD COLUMN "k_backs_up_to" INT NULL;
ALTER TABLE "FileEntry" ADD COLUMN "k_backup_of" INT NULL;

ALTER TABLE "StorageEntry" ADD CONSTRAINT "fk_backs_up_to" FOREIGN KEY (K_BACKS_UP_TO) REFERENCES "StorageEntry"(id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE "FileEntry" ADD CONSTRAINT "fk_backup_of" FOREIGN KEY (K_BACKUP_OF) REFERENCES "FileEntry"(id) DEFERRABLE INITIALLY DEFERRED;

# -- !Downs
ALTER TABLE "StorageEntry" DROP CONSTRAINT "fk_backs_up_to";
ALTER TABLE "FileEntry" DROP CONSTRAINT "fk_backup_of";

ALTER TABLE "StorageEntry" DROP COLUMN "k_backs_up_to";
ALTER TABLE "FileEntry" DROP COLUMN "k_backup_of";