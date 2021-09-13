package main

import (
	"bufio"
	"flag"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	path2 "path"
	"strings"
)

func loadList(filepathPtr *string) (*[]string, error) {
	f, err := os.Open(*filepathPtr)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	results := make([]string, 0)
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		results = append(results, scanner.Text())
	}
	return &results, nil
}

func checkExists(filename string, basePathPtr *string) (*string, error) {
	path := path2.Join(*basePathPtr, filename)
	_, err := os.Stat(path)
	if err==nil {
		return &path, nil
	} else if os.IsNotExist(err) {
		return nil, nil
	} else {
		return nil, err
	}
}

/**
Finds the first valid project file matching the given vsid in the given path
 */
func findFile(vsid string, basePathPtr *string) (*string, error) {
	possibleExtensions := []string{".cpr",".aep",".prproj",".plproj"}

	for _, xtn := range possibleExtensions {
		exists, err := checkExists(vsid + xtn, basePathPtr)
		if exists!=nil {
			return exists, nil
		} else if err != nil {
			return nil, err
		}
	}
	return nil, nil
}

func loadBearerToken(filePath *string) (*string,error) {
	f, err := os.Open(*filePath)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	content, err := ioutil.ReadAll(f)
	if err != nil {
		return nil, err
	}
	contentStr := strings.TrimRight(string(content),"\n\t ")
	return &contentStr,nil
}

func main() {
	wd, err := os.Getwd()
	if err != nil {
		log.Printf("ERROR can't get current working directory: %s", err)
		os.Exit(1)
	}

	httpClient := &http.Client{}

	inputList := flag.String("list", "missing-project-vsid.lst", "newline-delimited file of vsids to look for")
	scanPath := flag.String("path", wd, "path to search in (non-recursive)")
	plutocoreBase := flag.String("url","http://localhost:8000", "base URL to pluto-core, no trailing /")
	storageId := flag.Int("storageId", 1, "storage ID to import the files onto")
	bearerTokenFile := flag.String("bearer","token.secret", "file that contains the bearer token to use for auth")
	flag.Parse()

	vsidList,err := loadList(inputList)
	if err != nil {
		log.Printf("ERROR Could not load %s: %s", *inputList, err)
		os.Exit(1)
	}

	bearerTokenContent, err := loadBearerToken(bearerTokenFile)
	if err != nil {
		log.Printf("ERROR Could not load bearer token data from %s: %s", *bearerTokenFile, err)
		os.Exit(1)
	}

	for _, vsid := range *vsidList {
		maybePathPtr, err := findFile(vsid, scanPath)
		if err != nil {
			log.Fatalf("ERROR Could not scan for %s: %s", vsid, err)
		}
		if maybePathPtr!=nil {
			log.Printf("Got %s\t%s\n", vsid, *maybePathPtr)
			sendErr := sendToPlutoCore(httpClient, *plutocoreBase, *maybePathPtr, vsid, *storageId, bearerTokenContent)
			if sendErr != nil {
				log.Printf("ERROR Could not send %s to %s as %s: %s", *maybePathPtr, *plutocoreBase, vsid, sendErr)
				os.Exit(2)
			}
		}
	}
	log.Printf("All done")
}
