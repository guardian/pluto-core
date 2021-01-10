package main

import (
	"database/sql"
	"fmt"
	"log"
	"strings"
)

func logQuery(stmt string, arg1 int64, arg2 string) {
	replacement_1 := strings.Replace(stmt, "$1", fmt.Sprintf("%d", arg1), -1)
	replacement_2 := strings.Replace(replacement_1, "$2", arg2, -1)
	fmt.Printf("DEBUG I would execute %s", replacement_2)
}

func WriteDest(destDB *sql.DB, records chan NewPlutoAssetRecord) (chan interface{}, chan error) {
	doneChan := make(chan interface{})
	errChan := make(chan error, 1)

	go func() {
		updateStmt := `INSERT INTO "ProjectMetadata" (k_project_entry, s_key, s_value) VALUES ($1, "created_asset_folder", $2)`
		for {
			nextRecord := <-records
			if nextRecord.CoreProjectId == 0 {
				log.Print("INFO WriteDest received 0 project id, terminating")
				doneChan <- true
				return
			}

			logQuery(updateStmt, nextRecord.CoreProjectId, nextRecord.AssetFolderPath)
			//_, dbErr := destDB.Exec(updateStmt, nextRecord.CoreProjectId, nextRecord.AssetFolderPath)
			//if dbErr != nil {
			//	log.Print("ERROR WriteDest could not write to database: ", dbErr)
			//	errChan <- dbErr
			//  doneChan <- true
			//	return
			//}
		}
	}()

	return doneChan, errChan
}

func FindPlutoProjectId(destDb *sql.DB, vidispineId string) (int64, error) {
	rows, err := destDb.Query(`SELECT id FROM "ProjectEntry" WHERE vidispineId=$1`, vidispineId)
	if err != nil {
		return 0, err
	}
	defer rows.Close()

	var projectId int64
	readErr := rows.Scan(&projectId)
	if readErr != nil {
		return 0, readErr
	}
	return projectId, nil
}
