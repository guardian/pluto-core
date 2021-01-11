package vidispine

import "testing"

func TestVidispineSearch(t *testing.T) {
	s := NewVidispineSearchDoc()

	s.Add("field", "value1", "value2")
	byteContent := s.XMLString()
	str := string(byteContent)

	if str != `<ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
    <field>
        <name>field</name>
        <value>value1</value>
        <value>value2</value>
    </field>
</ItemSearchDocument>` {
		t.Error("item search document came out unexpected, see test source")
		t.Error("got ", str)
	}
}
