package vidispine

import (
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"strings"
)

type ShapeFile struct {
	ID    string   `json:"id"`
	Path  string   `json:"path"` //actually filename
	Uri   []string `json:"uri"`  //this is the one we want
	State string   `json:"state"`
	Size  int64    `json:"size"`
	Hash  string   `json:"hash"`
}

type ShapeComponent struct {
	Format string      `json:"format"`
	File   []ShapeFile `json:"file"`
}

type VidispineShape struct {
	Id                 string           `json:"id"`
	EssenceVersion     int              `json:"essenceVersion"`
	Tag                []string         `json:"tag"`
	MimeType           []string         `json:"mimeType"`
	ContainerComponent ShapeComponent   `json:"containerComponent"`
	AudioComponent     []ShapeComponent `json:"audioComponent"`
	VideoComponent     []ShapeComponent `json:"videoComponent"`
}

type httpClientIf interface {
	Do(r *http.Request) (*http.Response, error)
}

type httpMockClient struct {
	RequestsMade       []http.Request
	SuccessfulResponse *http.Response
	MultiResponseList  []*http.Response
	ErrorReturn        error
}

func (m *httpMockClient) Do(r *http.Request) (*http.Response, error) {
	m.RequestsMade = append(m.RequestsMade, *r)
	if m.MultiResponseList != nil {
		callCount := len(m.RequestsMade)
		var responseIndex int
		if callCount-1 > len(m.MultiResponseList) {
			responseIndex = len(m.MultiResponseList)
		} else {
			responseIndex = callCount - 1
		}
		return m.MultiResponseList[responseIndex], nil
	} else if m.SuccessfulResponse != nil {
		return m.SuccessfulResponse, nil
	} else {
		return nil, m.ErrorReturn
	}
}

func NewHttpMockClient() httpClientIf {
	return &httpMockClient{
		RequestsMade:       make([]http.Request, 0),
		SuccessfulResponse: nil,
		ErrorReturn:        errors.New("no data was set for the mock to return"),
	}
}

/**
try to open the given shape for download and return a ReadCloser that can be used to get the data
*/
func (f ShapeFile) OpenForDownload(baseUri *url.URL, user string, passwd string, client httpClientIf) (io.ReadCloser, error) {
	targetUri := *baseUri
	targetUri.Path = baseUri.Path + "/API/storage/file/" + f.ID + "/data"

	log.Print("DEBUG OpenForDownload url is ", targetUri.String())
	headers := http.Header{}
	headers.Add("Authorization", BasicAuthCredentials(user, passwd))

	rq := &http.Request{
		Method: "GET",
		URL:    &targetUri,
		Header: headers,
	}

	resp, httpErr := client.Do(rq)
	if httpErr != nil {
		log.Print("ERROR OpenForDownload could not communicate: ", httpErr)
		return nil, httpErr
	}

	if resp.StatusCode != 200 {
		log.Print("ERROR OpenForDownload could not stream data, server returned ", resp.StatusCode)
		return nil, errors.New(fmt.Sprintf("server error %d", resp.StatusCode))
	}
	return resp.Body, nil
}

func (s VidispineShape) AllFiles() []ShapeFile {
	allFileList := make([]ShapeFile, len(s.ContainerComponent.File))

	for i, f := range s.ContainerComponent.File {
		allFileList[i] = f
	}
	for _, component := range s.VideoComponent {
		for _, f := range component.File {
			allFileList = append(allFileList, f)
		}
	}
	for _, component := range s.AudioComponent {
		for _, f := range component.File {
			allFileList = append(allFileList, f)
		}
	}
	return allFileList

}

/**
returns a slice of zero or more VidispineShape that match the given shape tag
*/
func (i *VidispineItem) FindShapeFor(shapetag string) []VidispineShape {
	results := make([]VidispineShape, 0)

	for _, s := range i.Shapes {
		for _, tag := range s.Tag {
			if tag == shapetag {
				results = append(results, s)
			}
		}
	}
	return results
}

/**
returns a list of all the unique URIs for each file of each component on the shape,
that start with the given prefix e.g. "file://".
call with an empty string to get everything
*/
func (s *VidispineShape) GetURIs(uriPrefix string) []*url.URL {
	uniqUriStrings := make(map[string]int, 0)

	for _, audioComponent := range s.AudioComponent {
		for _, audioFile := range audioComponent.File {
			for _, uriString := range audioFile.Uri {
				if strings.HasPrefix(uriString, uriPrefix) {
					uniqUriStrings[uriString] = 0
				}
			}
		}
	}

	for _, videoComponent := range s.VideoComponent {
		for _, videoFile := range videoComponent.File {
			for _, uriString := range videoFile.Uri {
				if strings.HasPrefix(uriString, uriPrefix) {
					uniqUriStrings[uriString] = 0
				}
			}
		}
	}

	for _, containerFile := range s.ContainerComponent.File {
		for _, uriString := range containerFile.Uri {
			if strings.HasPrefix(uriString, uriPrefix) {
				if strings.HasPrefix(uriString, uriPrefix) {
					uniqUriStrings[uriString] = 0
				}
			}
		}
	}

	results := make([]*url.URL, len(uniqUriStrings))
	i := 0
	for uriString, _ := range uniqUriStrings {
		parsedUrl, urlErr := url.Parse(uriString)
		if urlErr == nil {
			results[i] = parsedUrl
			i += 1
		} else {
			log.Printf("WARNING GetURIs found uri %s that did not parse: %s", uriString, urlErr)
		}
	}

	return results
}

func BasicAuthCredentials(user string, passwd string) string {
	temp := fmt.Sprintf("%s:%s", user, passwd)
	return "Basic " + base64.StdEncoding.EncodeToString([]byte(temp))
}
