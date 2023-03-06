package main

import (
	"database/sql"
	"log"
)

func WriteDest(destDB *sql.DB, records chan ProjectRecord) (chan interface{}, chan error) {
	doneChan := make(chan interface{})
	errChan := make(chan error, 1)

	go func() {
		updateStmt := `UPDATE "ProjectEntry" SET b_deeparchive=$1,b_sensitive=$2,b_deletable=$3 where s_vidispine_id=$4`
		for {
			nextRecord := <-records
			if nextRecord.VSID == "" {
				log.Print("INFO WriteDest received empty project id, terminating")
				doneChan <- true
				return
			}

			//logQuery(updateStmt, nextRecord.CoreProjectId, nextRecord.AssetFolderPath)
			_, dbErr := destDB.Exec(updateStmt, nextRecord.isDeep, nextRecord.isSensitive, nextRecord.isDeletable, nextRecord.VSID)
			if dbErr != nil {
				log.Print("ERROR WriteDest could not write to database: ", dbErr)
				errChan <- dbErr
				doneChan <- true
				return
			}
		}
	}()

	return doneChan, errChan
}
