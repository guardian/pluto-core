package vidispine

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"strings"
)

type VidispineCommunicator interface {
	GetMetadataSource(itemId string, wantContentType string) (io.ReadCloser, error)
	LookupItemShapes(ctx context.Context, itemId string) (*VidispineItem, error)
	PerformItemSearch(search *VidispineSearchDoc, pagination *Pagination) (*[]VidispineItem, error)
	PerformItemSearchAsync(search *VidispineSearchDoc, pageSize int) (chan *VidispineItem, chan error)
	PerformCollectionSearch(search *VidispineSearchDoc, pagination *Pagination, mdFields []string) (*[]VidispineCollection, error)
	PerformCollectionSearchAsync(search *VidispineSearchDoc, pageSize int, mdFields []string) (chan *VidispineCollection, chan error)
	//PerformFilesystemImport(filepath string) (string, error)
}

type VidispineCommunicatorImp struct {
	VSBaseUri    *url.URL
	VSUser       string
	VSPasswd     string
	httpClient   httpClientIf
	v4Compatible bool
}

type ItemSearchResponse struct {
	Hits int64           `json:"hits"`
	Item []VidispineItem `json:"item"`
}

type CollectionSearchResponse struct {
	Hits       int64                 `json:"hits"`
	Collection []VidispineCollection `json:"collection"`
}

/**
initialise a new VidispineCommunicator object
*/
func NewVidispineCommunicator(vsBaseURI *url.URL, vsUser string, vsPasswd string, v4Compatible bool) VidispineCommunicator {
	copiedBaseUri := *vsBaseURI
	return &VidispineCommunicatorImp{
		VSBaseUri:    &copiedBaseUri,
		VSUser:       vsUser,
		VSPasswd:     vsPasswd,
		httpClient:   http.DefaultClient,
		v4Compatible: v4Compatible,
	}
}

/**
initiate download of metadata for the item, in the requested format.
returns an io.ReadCloser that will yield the content if the request was successful and we got a 200 response
otherwise returns a basic error, having logged out details to console.
*/
func (v *VidispineCommunicatorImp) GetMetadataSource(itemId string, wantContentType string) (io.ReadCloser, error) {
	url := *v.VSBaseUri
	url.Path = url.Path + fmt.Sprintf("/API/item/%s/metadata", itemId)
	headers := http.Header{}

	headers.Add("Accept", wantContentType)
	headers.Add("Authorization", BasicAuthCredentials(v.VSUser, v.VSPasswd))
	log.Printf("INFO VidispineCommunicatorImp getMetadataSource URL for metadata is %s", url.String())
	req, buildErr := http.NewRequest("GET", url.String(), nil)
	if buildErr != nil {
		log.Print("ERROR VidispineCommunicatorImp getMetadataSource could not build request: ", buildErr)
		return nil, buildErr
	}
	req.Header = headers

	resp, err := v.httpClient.Do(req)
	if err != nil {
		log.Print("ERROR VidispineCommunicatorImp getMetadataSource could not perform request: ", err)
		return nil, err
	}

	if resp.StatusCode != 200 {
		serverResponse, readErr := ioutil.ReadAll(resp.Body)
		if readErr != nil {
			log.Printf("ERROR VidispineCommunicatorImp getMetadataSource server returned %d but could not get server error body: %s", resp.StatusCode, readErr)
		} else {
			log.Printf("ERROR VidispineCommunicatorImp getMetadataSource server error %d. Server said %s", resp.StatusCode, string(serverResponse))
		}
		return nil, errors.New("server error")
	}
	return resp.Body, nil
}

type Pagination struct {
	First  int
	Number int
}

/**
get a list of all the shapes from the given item
*/
func (v *VidispineCommunicatorImp) LookupItemShapes(ctx context.Context, itemId string) (*VidispineItem, error) {
	bytes, err := v.internalItemsRequestWithContext(ctx, "/API/item/"+itemId, "content=shape,metadata,file", "GET", nil, nil, "")
	if err != nil {
		return nil, err
	}

	var shapesResponse VidispineItem
	unmarshalErr := json.Unmarshal(*bytes, &shapesResponse)
	if unmarshalErr != nil {
		log.Printf("Offending data was %s", string(*bytes))
		log.Print("ERROR internalItemsRequest could not understand content: ", unmarshalErr)
		return nil, unmarshalErr
	}

	return &shapesResponse, nil
}

func (v *VidispineCommunicatorImp) internalItemsRequest(apiPath string, rawQuery string, method string, body io.ReadCloser, pagination *Pagination, contentType string) (*[]byte, error) {
	return v.internalItemsRequestWithContext(context.Background(), apiPath, rawQuery, method, body, pagination, contentType)
}

func (v *VidispineCommunicatorImp) internalRequest(ctx context.Context, targetUri url.URL, method string, body io.ReadCloser, contentType string) (*[]byte, error) {
	headers := http.Header{}
	headers.Add("Accept", "application/json")
	headers.Add("Authorization", BasicAuthCredentials(v.VSUser, v.VSPasswd))
	if body != nil {
		headers.Add("Content-Type", contentType)
	}
	rq := &http.Request{
		Method: method,
		URL:    &targetUri,
		Header: headers,
		Body:   body,
	}

	resp, httpErr := v.httpClient.Do(rq.WithContext(ctx))
	if httpErr != nil {
		log.Print("ERROR internalRequest could not communicate: ", httpErr)
		return nil, httpErr
	}

	defer resp.Body.Close()
	bytes, readErr := ioutil.ReadAll(resp.Body)
	if readErr != nil {
		log.Print("ERROR internalRequest could not read data: ", readErr)
		return nil, httpErr
	}

	if resp.StatusCode != 200 {
		log.Printf("ERROR internalRequest server returned %d: %s", resp.StatusCode, string(bytes))
		return nil, errors.New(fmt.Sprintf("server error %d", resp.StatusCode))
	}

	return &bytes, nil
}

func (v *VidispineCommunicatorImp) internalItemsRequestWithContext(ctx context.Context, apiPath string, rawQuery string, method string, body io.ReadCloser, pagination *Pagination, contentType string) (*[]byte, error) {
	targetUri := *v.VSBaseUri
	targetUri.Path = v.VSBaseUri.Path + apiPath
	targetUri.RawQuery = rawQuery

	if pagination != nil {
		if v.v4Compatible {
			//vidispine 4.x and below requires matrix parameters for first and number
			targetUri.Path += fmt.Sprintf(";first=%d;number=%d", pagination.First, pagination.Number)
		} else {
			//vidispine 5.x supports everything in query params
			targetUri.RawQuery += fmt.Sprintf("&first=%d&number=%d", pagination.First, pagination.Number)
		}
	}

	log.Printf("DEBUG internalItemsRequest uri is %s", targetUri.String())

	return v.internalRequest(ctx, targetUri, method, body, contentType)
}

/**
retrieves a single page of results from a VS item search, as specified by the "pagination" parameter
*/
func (v *VidispineCommunicatorImp) PerformItemSearch(search *VidispineSearchDoc, pagination *Pagination) (*[]VidispineItem, error) {
	bytes, err := v.internalItemsRequest("/API/item", "content=shape,metadata,file", "PUT", ioutil.NopCloser(search.XMLReader()), pagination, "application/xml")

	if err != nil {
		return nil, err
	}
	var searchResponse ItemSearchResponse
	unmarshalErr := json.Unmarshal(*bytes, &searchResponse)
	if unmarshalErr != nil {
		log.Printf("Offending data was %s", string(*bytes))
		log.Print("ERROR internalItemsRequest could not understand content: ", unmarshalErr)
		return nil, unmarshalErr
	}

	return &(searchResponse.Item), nil
}

/**
retrieves a single page of results from a VS item search, as specified by the "pagination" parameter
*/
func (v *VidispineCommunicatorImp) PerformCollectionSearch(search *VidispineSearchDoc, pagination *Pagination, mdFields []string) (*[]VidispineCollection, error) {
	var rawQuery string
	if len(mdFields) > 0 {
		rawQuery = "content=metadata&field=" + strings.Join(mdFields, ",")
	} else {
		rawQuery = "content=metadata"
	}
	bytes, err := v.internalItemsRequest("/API/collection", rawQuery, "PUT", ioutil.NopCloser(search.XMLReader()), pagination, "application/xml")

	if err != nil {
		return nil, err
	}
	var searchResponse CollectionSearchResponse
	unmarshalErr := json.Unmarshal(*bytes, &searchResponse)
	if unmarshalErr != nil {
		log.Printf("Offending data was %s", string(*bytes))
		log.Print("ERROR internalItemsRequest could not understand content: ", unmarshalErr)
		return nil, unmarshalErr
	}

	return &(searchResponse.Collection), nil
}

/**
asynchronously retrieves _all_ results from a VS item search. retrievals are done in blocks of `pageSize` and the items are
yielded onto a channel that is returned
*/
func (v *VidispineCommunicatorImp) PerformItemSearchAsync(search *VidispineSearchDoc, pageSize int) (chan *VidispineItem, chan error) {
	outputChan := make(chan *VidispineItem, pageSize*2)
	errChan := make(chan error, 1)

	go func() {
		count := 1
		for {
			p := Pagination{
				First:  count,
				Number: pageSize,
			}
			nextPage, pageErr := v.PerformItemSearch(search, &p)
			if pageErr != nil {
				log.Printf("ERROR VidispineCommunicatorImp.PerformItemSearchAsync could not retrieve page of results: %s", pageErr)
				errChan <- pageErr
				return
			}

			if len(*nextPage) == 0 {
				outputChan <- nil
				close(outputChan)
				return
			}

			for _, entry := range *nextPage {
				//always remember: don't take the address of an iterator!!
				copiedEntry := entry
				outputChan <- &copiedEntry
			}

			count += len(*nextPage)
		}
	}()

	return outputChan, errChan
}

/**
asynchronously retrieves _all_ results from a VS collection search. retrievals are done in blocks of `pageSize` and the items are
yielded onto a channel that is returned
*/
func (v *VidispineCommunicatorImp) PerformCollectionSearchAsync(search *VidispineSearchDoc, pageSize int, mdFields []string) (chan *VidispineCollection, chan error) {
	outputChan := make(chan *VidispineCollection, pageSize*2)
	errChan := make(chan error, 1)

	go func() {
		count := 1
		for {
			p := Pagination{
				First:  count,
				Number: pageSize,
			}
			nextPage, pageErr := v.PerformCollectionSearch(search, &p, mdFields)
			if pageErr != nil {
				log.Printf("ERROR VidispineCommunicatorImp.PerformItemSearchAsync could not retrieve page of results: %s", pageErr)
				errChan <- pageErr
				return
			}

			if len(*nextPage) == 0 {
				outputChan <- nil
				close(outputChan)
				return
			}

			for _, entry := range *nextPage {
				//always remember: don't take the address of an iterator!!
				copiedEntry := entry
				outputChan <- &copiedEntry
			}

			count += len(*nextPage)
		}
	}()

	return outputChan, errChan
}

func findProxyFor(item *VidispineItem) *VidispineShape {
	videoProxyShapes := item.FindShapeFor("lowres")
	if len(videoProxyShapes) > 0 {
		return &videoProxyShapes[0]
	}
	audioProxyShapes := item.FindShapeFor("lowaudio")
	if len(audioProxyShapes) > 0 {
		return &audioProxyShapes[0]
	}
	imageProxyShapes := item.FindShapeFor("lowimage")
	if len(imageProxyShapes) > 0 {
		return &imageProxyShapes[0]
	}
	return nil
}

func findOriginalFor(item *VidispineItem) *VidispineShape {
	shapes := item.FindShapeFor("original")
	if len(shapes) > 0 {
		return &shapes[0]
	}
	return nil
}

type VidispineCommunicatorMock struct {
	GetMetadataSuccessData         []byte
	GetMetadataFailureErr          error
	GetMetadataCalledForItems      []string
	LookupItemShapesCalledForItems []string
	LookupItemShapesSuccessData    *VidispineItem
	LookupItemShapesError          error
}

func (v *VidispineCommunicatorMock) GetMetadataSource(itemId string, wantContentType string) (io.ReadCloser, error) {
	v.GetMetadataCalledForItems = append(v.GetMetadataCalledForItems, itemId)
	if v.GetMetadataSuccessData != nil {
		return ioutil.NopCloser(bytes.NewReader(v.GetMetadataSuccessData)), nil
	} else {
		return nil, v.GetMetadataFailureErr
	}
}

func (v *VidispineCommunicatorMock) LookupItemShapes(ctx context.Context, itemId string) (*VidispineItem, error) {
	v.LookupItemShapesCalledForItems = append(v.LookupItemShapesCalledForItems, itemId)
	if v.LookupItemShapesSuccessData != nil {
		rtn := *v.LookupItemShapesSuccessData
		return &rtn, nil //make a copy of the item when returning
	} else {
		return nil, v.LookupItemShapesError
	}
}

func (v *VidispineCommunicatorMock) PerformItemSearch(search *VidispineSearchDoc, pagination *Pagination) (*[]VidispineItem, error) {
	return nil, errors.New("mock does not implement PerformItemSearch yet")
}

func (v *VidispineCommunicatorMock) PerformItemSearchAsync(search *VidispineSearchDoc, pageSize int) (chan *VidispineItem, chan error) {
	outputChan := make(chan *VidispineItem, pageSize*2)
	errChan := make(chan error, 1)

	go func() {
		count := 1
		for {
			p := Pagination{
				First:  count,
				Number: pageSize,
			}
			nextPage, pageErr := v.PerformItemSearch(search, &p)
			if pageErr != nil {
				errChan <- pageErr
				return
			}

			if len(*nextPage) == 0 {
				close(outputChan)
				return
			}
			for _, entry := range *nextPage {
				outputChan <- &entry
			}
			count += len(*nextPage)
		}
	}()

	return outputChan, errChan
}

func (v *VidispineCommunicatorMock) PerformFilesystemImport(filepath string) (string, error) {
	return "", errors.New("mock does not implement PerformFilesystemImport")
}

func ShapeForXtn(filepath string) (string, bool) {
	downCaseFilepath := strings.ToLower(filepath)

	audioExtensions := []string{"aif", "aiff", "wav", "mp3", "m4a", "wma"}
	imageExtensions := []string{"jpg", "jpeg", "tif", "tiff", "tga", "pdf"}
	videoExtensions := []string{"mp4", "wmv", "mov", "mxf", "m4v", "mkv"}

	for _, xtn := range audioExtensions {
		if strings.HasSuffix(downCaseFilepath, xtn) {
			return "lowaudio", true
		}
	}
	for _, xtn := range videoExtensions {
		if strings.HasSuffix(downCaseFilepath, xtn) {
			return "lowres", true
		}
	}
	for _, xtn := range imageExtensions {
		if strings.HasSuffix(downCaseFilepath, xtn) {
			return "lowimage", true
		}
	}
	return "", true
}

//func (v *VidispineCommunicatorImp) PerformFilesystemImport(filepath string, fullXmlMetadata *[]byte) (string, error) {
//	targetUri, _ := url.Parse(fmt.Sprintf("%s/api/import", v.VSBaseUri))
//
//	queryParams := map[string]string {
//		"uri": url.QueryEscape(fmt.Sprintf("file://%s", filepath)),
//	}
//
//	if shapeTag, haveShapeTag := ShapeForXtn(filepath); haveShapeTag {
//		queryParams["tag"] = shapeTag
//	}
//
//	metaReader := ioutil.NopCloser(bytes.NewReader(*fullXmlMetadata))
//
//	v.internalRequest(context.Background(), *targetUri, "POST", metaReader, "application/xml")
//}
