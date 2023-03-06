package main

import (
	"database/sql"
	"log"
)

func ReadSource(sourceDb *sql.DB, bufferSize int) (chan OldPlutoAssetRecord, chan error) {
	recordsChannel := make(chan OldPlutoAssetRecord, bufferSize)
	errorChannel := make(chan error, bufferSize)

	go func() {
		rows, err := sourceDb.Query("SELECT project_id, asset_folder_path FROM \"gnm_asset_folder_assetfolder\"")
		if err != nil {
			log.Printf("ERROR ReadSource could not load in data from old database: %s", err)
			errorChannel <- err
			return
		}

		defer rows.Close()

		for rows.Next() {
			var record OldPlutoAssetRecord
			err := rows.Scan(&record.VSProjectId, &record.AssetFolderPath)
			if err != nil {
				log.Print("ERROR ReadSource could not read record: ", err)
				errorChannel <- err
				return
			}
			recordsChannel <- record
		}
		log.Print("INFO ReadSource read all records")
		recordsChannel <- OldPlutoAssetRecord{}
	}()

	return recordsChannel, errorChannel
}
