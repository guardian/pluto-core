package main

import (
	"database/sql"
	"flag"
	"fmt"
	_ "github.com/lib/pq"
	"log"
)

func openConnection(host *string, user *string, passwd *string, dbName *string, noSSL *bool) (*sql.DB, error) {
	connStr := fmt.Sprintf("host=%s user=%s password=%s dbname=%s", *host, *user, *passwd, *dbName)
	if *noSSL {
		connStr += " sslmode=disable"
	}
	return sql.Open("postgres", connStr)
}

func main() {
	noSSL := flag.Bool("nossl", false, "don't use SSL when connecting")
	sourceHostPtr := flag.String("source-host", "localhost", "hostname (or unix socket) running source database")
	sourceUserPtr := flag.String("source-user", "postgres", "user to access host db as")
	sourcePasswdPtr := flag.String("source-passwd", "", "password for source database")
	sourceDbNamePtr := flag.String("source-db", "pluto", "name of the source database")
	destHostPtr := flag.String("dest-host", "localhost", "hostname (or unix socket) running db to write to")
	destUserPtr := flag.String("dest-user", "postgres", "user to access write db as")
	destPasswdPtr := flag.String("dest-passwd", "", "password for destination database")
	destDbNamePtr := flag.String("dest-db", "projectlocker", "name of the destination database")
	vidispineSiteId := flag.String("vs-site", "VX", "vidispine site identifier")
	flag.Parse()

	sourceDb, sourceErr := openConnection(sourceHostPtr, sourceUserPtr, sourcePasswdPtr, sourceDbNamePtr, noSSL)
	if sourceErr != nil {
		log.Fatalf("Could not connect to source database %s as %s: %s ", *sourceHostPtr, *sourceUserPtr, sourceErr)
	}

	destDb, destErr := openConnection(destHostPtr, destUserPtr, destPasswdPtr, destDbNamePtr, noSSL)
	if destErr != nil {
		log.Fatalf("Could not connect to source database %s as %s: %s ", *destHostPtr, *destUserPtr, destErr)
	}

	log.Print("TRACE main starting up source reader")
	incomingRecordsChnl, incomingErrChnl := ReadSource(sourceDb, 20)

	log.Print("TRACE main starting up dest writer")
	outgoingRecordsChnl := make(chan NewPlutoAssetRecord, 20)
	outgoingDoneChnl, outgoingErrChnl := WriteDest(destDb, outgoingRecordsChnl)

	log.Print("TRACE main starting processing loop")
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
				log.Printf("DEBUG got %s, looking up", vsid)
				projectId, lookupErr := FindPlutoProjectId(destDb, vsid)
				if lookupErr != nil {
					log.Printf("ERROR Could not look up %s in pluto-core: %s", vsid, lookupErr)
				} else {
					log.Printf("DEBUG got %d for %s", projectId, vsid)
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
