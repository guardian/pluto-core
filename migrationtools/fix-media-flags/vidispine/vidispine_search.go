package vidispine

import (
	"bytes"
	"encoding/xml"
	"io"
)

type VidispineSearchDoc struct {
	fields map[string][]string
}

func NewVidispineSearchDoc() *VidispineSearchDoc {
	return &VidispineSearchDoc{fields: make(map[string][]string, 0)}
}

func (v *VidispineSearchDoc) Add(field string, values ...string) {
	v.fields[field] = values
}

func (v *VidispineSearchDoc) XMLString() []byte {
	buf := bytes.Buffer{}

	encoder := xml.NewEncoder(&buf)
	encoder.Indent("", "    ")
	encoder.EncodeToken(xml.StartElement{
		Attr: []xml.Attr{
			{
				Name:  xml.Name{Local: "xmlns"},
				Value: "http://xml.vidispine.com/schema/vidispine",
			},
		},
		Name: xml.Name{Local: "ItemSearchDocument"},
	})

	for fieldName, values := range v.fields {
		encoder.EncodeToken(xml.StartElement{
			Name: xml.Name{Local: "field"},
			Attr: nil,
		})
		encoder.EncodeToken(xml.StartElement{
			Name: xml.Name{Local: "name"},
			Attr: nil,
		})
		encoder.Flush()
		xml.EscapeText(&buf, []byte(fieldName))
		encoder.EncodeToken(xml.EndElement{Name: xml.Name{Local: "name"}})
		for _, value := range values {
			encoder.EncodeToken(xml.StartElement{
				Name: xml.Name{Local: "value"},
				Attr: nil,
			})
			encoder.Flush()
			xml.EscapeText(&buf, []byte(value))
			encoder.EncodeToken(xml.EndElement{Name: xml.Name{Local: "value"}})
		}
		encoder.EncodeToken(xml.EndElement{Name: xml.Name{Local: "field"}})
	}
	encoder.EncodeToken(xml.EndElement{Name: xml.Name{Local: "ItemSearchDocument"}})
	encoder.Flush()
	return buf.Bytes()
}

func (v *VidispineSearchDoc) XMLReader() io.Reader {
	return bytes.NewReader(v.XMLString())
}
