package main

import (
	"errors"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path"
	"strings"
)

func getModTime(filepath string) (string, error) {
	info, err := os.Stat(filepath)
	if err != nil {
		return "", err
	}

	return fmt.Sprintf("%d",info.ModTime().Unix()), nil
}

func sendToPlutoCore(cli *http.Client, baseUrl string, filepath string, vsid string, storageId int, bearerTokenContent *string) error {
	log.Printf("Sending %s as vsid %s to %s...", filepath, vsid, baseUrl)
	modTimeString, err := getModTime(filepath)
	if err != nil {
		return err
	}

	fileContent, err := os.Open(filepath)
	if err != nil {
		return err
	}
	defer fileContent.Close()

	args := []string{
		"vsid="+vsid,
		"filename="+path.Base(filepath),
		"storage="+ fmt.Sprintf("%d", storageId),
		"modtime=" + modTimeString,
	}

	targetUrl := fmt.Sprintf("%s/api/migration/importProblematicProject?%s", baseUrl, strings.Join(args,"&"))
	req, err := http.NewRequest("POST", targetUrl, fileContent)
	if err != nil {
		return err
	}
	req.Header.Add("Authorization", fmt.Sprintf("Bearer %s", *bearerTokenContent))

	response, err := cli.Do(req)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	responseContent, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return err
	}

	switch response.StatusCode {
	case 200:
		log.Printf("Sent %s ok", filepath)
		return nil
	case 401:
		fallthrough
	case 403:
		log.Printf("Permission denied, maybe the token expired?")
		return errors.New("permission denied")
	default:
		log.Printf("Server responded %d %s", response.StatusCode, string(responseContent))
		return errors.New(fmt.Sprintf("server error %d", response.StatusCode))
	}
}
