package main

import (
	"flag"
	"github.com/fredex42/fix-media-flags/vidispine"
	"log"
	"net/url"
)

type ProjectRecord struct {
	VSID        string
	isSensitive bool
	isDeletable bool
	isDeep      bool
}

/**
returns the number of times the given expectedValue appears in the given field
*/
func CheckForMatches(ts *vidispine.VSMetaTimespan, fieldName string, expectedValue string) int {
	results, fieldExists := ts.ValueForField(fieldName)
	if fieldExists {
		var i = 0
		for _, entry := range results {
			if entry == expectedValue {
				i += 1
			}
		}
		return i
	} else {
		return 0
	}
}

func main() {
	vsBaseUriPtr := flag.String("vs-base", "http://localhost:8080", "Vidispine base uri")
	vsUserPtr := flag.String("vs-user", "admin", "Vidispine username")
	vsPasswdPtr := flag.String("vs-pass", "", "Vidispine password")
	resultPageSize := flag.Int("page-size", 100, "number of records to get at once")
	flag.Parse()

	vsUri, uriErr := url.Parse(*vsBaseUriPtr)
	if uriErr != nil {
		log.Fatal("Base URI was not valid: ", uriErr)
	}

	vsComm := vidispine.NewVidispineCommunicator(vsUri, *vsUserPtr, *vsPasswdPtr, true)

	doc := vidispine.NewVidispineSearchDoc()
	doc.Add("gnm_type", "project")
	//
	//interestingFields := []string{
	//	"gnm_storage_rule_deeparchive",
	//	"gnm_storage_rule_deletable",
	//	"gnm_storage_rule_sensitive",
	//}

	resultsChan, errChan := vsComm.PerformCollectionSearchAsync(doc, *resultPageSize, []string{})

	func() {
		for {
			select {
			case record := <-resultsChan:
				if record == nil {
					log.Print("Received nil record, terminating")
					return
				}
				var project ProjectRecord

				ts, hasTs := record.Metadata.DefaultTimespan()
				if !hasTs {
					project = ProjectRecord{
						VSID:        record.Id,
						isSensitive: false,
						isDeletable: false,
						isDeep:      false,
					}
				} else {
					sensCount := CheckForMatches(&ts, "gnm_storage_rule_sensitive", "storage_rule_sensitive")
					deletableCount := CheckForMatches(&ts, "gnm_storage_rule_deletable", "storage_rule_deletable")
					deepCount := CheckForMatches(&ts, "gnm_storage_rule_deep_archive", "storage_rule_deep_archive")

					log.Printf("DEBUG %s (%s) sensCount %d delCount %d deepCount %d", record.Id, record.Name, sensCount, deletableCount, deepCount)
					project = ProjectRecord{
						VSID:        record.Id,
						isSensitive: sensCount > 0,
						isDeletable: deletableCount > 0,
						isDeep:      deepCount > 0,
					}
				}
				log.Printf("DEBUG ProjectRecord is %v", project)
			case err := <-errChan:
				log.Printf("ERROR from VS search: %s", err)
				return
			}
		}
	}()

	log.Print("All done")
}
