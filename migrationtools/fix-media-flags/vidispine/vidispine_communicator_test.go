package vidispine

import (
	"bytes"
	"context"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"testing"
)

/**
GetMetadataSource should return a byte source streaming the content of the given item's metadata
*/
func TestGetMetadataSource(t *testing.T) {
	uri, _ := url.Parse("https://test-server/test/path")
	c := &VidispineCommunicatorImp{
		VSBaseUri:  uri,
		VSUser:     "user",
		VSPasswd:   "passwd",
		httpClient: NewHttpMockClient(),
	}

	mockData := []byte("item metadata goes here")
	c.httpClient.(*httpMockClient).SuccessfulResponse = &http.Response{
		Status:        "",
		StatusCode:    200,
		Body:          ioutil.NopCloser(bytes.NewReader(mockData)),
		ContentLength: int64(len(mockData)),
	}

	result, err := c.GetMetadataSource("VX-1234", "application/json")
	if err != nil {
		t.Error("GetMetadataSource returned an unexpected error: ", err)
		t.FailNow()
	}
	defer result.Close() //not _technically_ needed as it's a no-op closer but it won't hurt

	returnedContent, _ := ioutil.ReadAll(result)
	if string(returnedContent) != string(mockData) {
		t.Error("returned content did not match mock data from test")
	}

	requests := c.httpClient.(*httpMockClient).RequestsMade
	if len(requests) != 1 {
		t.Error("Expected 1 http request made but got ", len(requests))
	}

	if requests[0].Method != "GET" {
		t.Error("Expected HTTP GET request but got", requests[0].Method)
	}
	if requests[0].Header.Get("Accept") != "application/json" {
		t.Error("Expected Accept: application/json but got ", requests[0].Header.Get("Accept"))
	}
	if requests[0].Header.Get("Authorization") != "Basic dXNlcjpwYXNzd2Q=" {
		t.Error("Expected basic-auth authorization but got ", requests[0].Header.Get("Authorization"))
	}
	if requests[0].URL.String() != "https://test-server/test/path/API/item/VX-1234/metadata" {
		t.Error("Expected http path of https://test-server/test/path/API/item/VX-1234/metadata but got ", requests[0].URL.String())
	}
}

/**
GetMetadataSource should return an error if response is not 200
*/
func TestGetMetadataSourceError(t *testing.T) {
	uri, _ := url.Parse("https://test-server/test/path")
	c := &VidispineCommunicatorImp{
		VSBaseUri:  uri,
		VSUser:     "user",
		VSPasswd:   "passwd",
		httpClient: NewHttpMockClient(),
	}

	mockData := []byte("error message goes here")
	c.httpClient.(*httpMockClient).SuccessfulResponse = &http.Response{
		Status:        "",
		StatusCode:    500,
		Body:          ioutil.NopCloser(bytes.NewReader(mockData)),
		ContentLength: int64(len(mockData)),
	}

	_, err := c.GetMetadataSource("VX-1234", "application/json")
	if err == nil {
		t.Error("GetMetadataSource did not return expected error")
		t.FailNow()
	}

	if err.Error() != "server error" {
		t.Error("Expected dummy error message, got ", err.Error())
	}
}

func TestLookupItemShapes(t *testing.T) {
	uri, _ := url.Parse("https://test-server/test/path")
	c := &VidispineCommunicatorImp{
		VSBaseUri:  uri,
		VSUser:     "user",
		VSPasswd:   "passwd",
		httpClient: NewHttpMockClient(),
	}

	sampleContentReader, testDataErr := os.Open("sample_shapes.json")
	if testDataErr != nil {
		t.Error("Could not open test data from sample_shapes.json: ", testDataErr)
		t.FailNow()
	}

	c.httpClient.(*httpMockClient).SuccessfulResponse = &http.Response{
		StatusCode: 200,
		Header:     nil,
		Body:       sampleContentReader,
	}

	result, err := c.LookupItemShapes(context.Background(), "VX-1234")
	if err != nil {
		t.Error("LookupItemShapes returned unexpected error: ", err)
	} else {
		fmt.Printf("%v", result)
		if result.ItemId != "KP-3610200" {
			t.Error("Got unexpected item id ", result.ItemId)
		}
		if len(result.Shapes) != 2 {
			t.Error("Got unexpected shape count ", len(result.Shapes))
		}

		proxyShape := result.FindShapeFor("lowaudio")
		if len(proxyShape) != 1 {
			t.Error("Expected 1 lowaudio proxy, got ", len(proxyShape))
		} else {
			if proxyShape[0].Id != "KP-4301210" {
				t.Error("Unexpected proxy shape id ", proxyShape[0].Id)
			}
		}

		originalShape := result.FindShapeFor("original")
		if len(originalShape) != 1 {
			t.Error("Expected 1 original shape, got ", len(originalShape))
		} else {
			if originalShape[0].Id != "KP-4301209" {
				t.Error("Unexpected original shape id ", originalShape[0].Id)
			}
		}
	}
}

func TestPerformItemSearch(t *testing.T) {
	uri, _ := url.Parse("https://test-server/test/path")
	c := &VidispineCommunicatorImp{
		VSBaseUri:    uri,
		VSUser:       "user",
		VSPasswd:     "passwd",
		httpClient:   NewHttpMockClient(),
		v4Compatible: true,
	}

	sampleContentReader, testDataErr := os.Open("sample_search.json")
	if testDataErr != nil {
		t.Error("Could not open test data from sample_search.json: ", testDataErr)
		t.FailNow()
	}

	c.httpClient.(*httpMockClient).SuccessfulResponse = &http.Response{
		StatusCode: 200,
		Body:       sampleContentReader,
	}

	doc := NewVidispineSearchDoc()
	doc.Add("gnm_type", "master")
	p := Pagination{
		First:  1,
		Number: 1,
	}
	result, err := c.PerformItemSearch(doc, &p)

	if err != nil {
		t.Error("PerformItemSearch returned unexpected error: ", err)
		t.FailNow()
	}

	if len(*result) != 1 {
		t.Error("Expected 1 result, got ", len(*result))
	}

	item := (*result)[0]
	if item.ItemId != "KP-3789007" {
		t.Error("Unexpected item id: ", item.ItemId)
	}

	httpMock := c.httpClient.(*httpMockClient)
	if len(httpMock.RequestsMade) != 1 {
		t.Error("Expected 1 http request, got ", len(httpMock.RequestsMade))
	}
	rq := httpMock.RequestsMade[0]
	if rq.Method != "PUT" {
		t.Error("HTTP request should have been PUT, got ", rq.Method)
	}
	if rq.URL.String() != "https://test-server/test/path/API/item;first=1;number=1?content=shape,metadata,file" {
		t.Error("Expected search URL to be https://test-server/test/path/API/item;first=1;number=1?content=shape,,metadata,file got ", rq.URL.String())
	}
}

func TestPerformItemSearchAsync(t *testing.T) {
	uri, _ := url.Parse("https://test-server/test/path")
	c := &VidispineCommunicatorImp{
		VSBaseUri:    uri,
		VSUser:       "user",
		VSPasswd:     "passwd",
		httpClient:   NewHttpMockClient(),
		v4Compatible: true,
	}

	sampleContentReader, testDataErr := os.Open("sample_search.json")
	if testDataErr != nil {
		t.Error("Could not open test data from sample_search.json: ", testDataErr)
		t.FailNow()
	}

	endContentReader, testDataErr := os.Open("sample_search_empty.json")
	if testDataErr != nil {
		t.Error("Could not open test data from sample_search.json: ", testDataErr)
		t.FailNow()
	}

	c.httpClient.(*httpMockClient).MultiResponseList = []*http.Response{
		{
			StatusCode: 200,
			Body:       sampleContentReader,
		},
		{
			StatusCode: 200,
			Body:       endContentReader,
		},
	}

	doc := NewVidispineSearchDoc()
	doc.Add("gnm_type", "master")

	resultChan, errChan := c.PerformItemSearchAsync(doc, 1)

	result := make([]VidispineItem, 0)
	func() {
		for {
			select {
			case err := <-errChan:
				t.Error("PerformItemSearch returned unexpected error: ", err)
				t.FailNow()
			case data := <-resultChan:
				if data == nil {
					return
				}
				result = append(result, *data)
			}
		}
	}()

	if len(result) != 1 {
		t.Error("Expected 1 result, got ", len(result))
	}

	item := result[0]
	if item.ItemId != "KP-3789007" {
		t.Error("Unexpected item id: ", item.ItemId)
	}

	httpMock := c.httpClient.(*httpMockClient)
	if len(httpMock.RequestsMade) != 2 {
		t.Error("Expected 2 http requests, got ", len(httpMock.RequestsMade))
	}
	rq := httpMock.RequestsMade[0]
	if rq.Method != "PUT" {
		t.Error("HTTP request should have been PUT, got ", rq.Method)
	}
	if rq.URL.String() != "https://test-server/test/path/API/item;first=1;number=1?content=shape,metadata,file" {
		t.Error("Expected search URL to be https://test-server/test/path/API/item;first=1;number=1?content=shape,metadata,file got ", rq.URL.String())
	}
}
