package main

type OldPlutoAssetRecord struct {
	VSProjectId     int64
	AssetFolderPath string
}

type NewPlutoAssetRecord struct {
	CoreProjectId   int64
	AssetFolderPath string
}
