package main

import (
	"database/sql"
	"flag"
	"fmt"
	_ "github.com/lib/pq"
	"log"
)

func openConnection(host *string, user *string, passwd *string) (*sql.DB, error) {
	connStr := fmt.Sprintf("host=%s user=%s password=%s", *host, *user, *passwd)
	return sql.Open("postgres", connStr)
}

func main() {
	sourceHostPtr := flag.String("source-host", "localhost", "postgres db to read from")
	sourceUserPtr := flag.String("source-user", "postgres", "user to access host db as")
	sourcePasswdPtr := flag.String("source-passwd", "", "password for source database")
	destHostPtr := flag.String("dest-host", "localhost", "postgres db to write to")
	destUserPtr := flag.String("dest-user", "postgres", "user to access write db as")
	destPasswdPtr := flag.String("dest-passwd", "", "password for destination database")
	vidispineSiteId := flag.String("vs-site", "VX", "vidispine site identifier")
	flag.Parse()

	sourceDb, sourceErr := openConnection(sourceHostPtr, sourceUserPtr, sourcePasswdPtr)
	if sourceErr != nil {
		log.Fatalf("Could not connect to source database %s as %s: %s ", *sourceHostPtr, *sourceUserPtr, sourceErr)
	}

	destDb, destErr := openConnection(destHostPtr, destUserPtr, destPasswdPtr)
	if destErr != nil {
		log.Fatalf("Could not connect to source database %s as %s: %s ", *destHostPtr, *destUserPtr, destErr)
	}

	incomingRecordsChnl, incomingErrChnl := ReadSource(sourceDb, 20)

	outgoingRecordsChnl := make(chan NewPlutoAssetRecord, 20)
	outgoingDoneChnl, outgoingErrChnl := WriteDest(destDb, outgoingRecordsChnl)

	func() {
		for {
			select {
			case newRecord := <-incomingRecordsChnl:
				if newRecord.VSProjectId == 0 {
					log.Printf("All source records read in")
					outgoingRecordsChnl <- NewPlutoAssetRecord{}
					return
				}
				vsid := fmt.Sprintf("%s-%d", *vidispineSiteId, newRecord.VSProjectId)
				projectId, lookupErr := FindPlutoProjectId(destDb, vsid)
				if lookupErr != nil {
					log.Printf("ERROR Could not look up %s in pluto-core: %s", vsid, lookupErr)
				} else {
					newRec := NewPlutoAssetRecord{
						CoreProjectId:   projectId,
						AssetFolderPath: newRecord.AssetFolderPath,
					}
					outgoingRecordsChnl <- newRec
				}
			case incomingErr := <-incomingErrChnl:
				log.Printf("ERROR Could not read incoming records: %s", incomingErr)
				outgoingRecordsChnl <- NewPlutoAssetRecord{}
				return
			case outgoingErr := <-outgoingErrChnl:
				log.Printf("ERROR Could not write outgoing records: %s", outgoingErr)
				return
			}
		}
	}()

	log.Printf("Waiting for writer to terminate")
	<-outgoingDoneChnl
	log.Printf("All done")
}
