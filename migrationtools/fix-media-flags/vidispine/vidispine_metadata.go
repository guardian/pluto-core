package vidispine

import (
	"fmt"
	"log"
)

type VSMetaValue struct {
	Value     string `json:"value"`
	Uuid      string `json:"uuid"`
	User      string `json:"user"`
	Timestamp string `json:"timestamp"`
	Change    string `json:"change"`
}

type VSMetaField struct {
	Name      string        `json:"name"`
	Value     []VSMetaValue `json:"value"`
	Uuid      string        `json:"uuid"`
	User      string        `json:"user"`
	Timestamp string        `json:"timestamp"`
	Change    string        `json:"change"`
}

/**
returns just the individual string values of the given field
*/
func (f VSMetaField) stringValues() []string {
	rtn := make([]string, len(f.Value))
	for i, v := range f.Value {
		rtn[i] = v.Value
	}
	return rtn
}

type VSMetaGroup struct {
	Name      string        `json:"name"`
	Field     []VSMetaField `json:"field"`
	Group     []VSMetaGroup `json:"group"`
	Uuid      string        `json:"uuid"`
	User      string        `json:"user"`
	Timestamp string        `json:"timestamp"`
	Change    string        `json:"change"`
}

func (g VSMetaGroup) FieldForName(fieldName string) (VSMetaField, bool) {
	for _, field := range g.Field {
		if field.Name == fieldName {
			return field, true
		}
	}
	return VSMetaField{}, false
}

func (g VSMetaGroup) ValueForField(fieldName string) ([]string, bool) {
	field, gotField := g.FieldForName(fieldName)
	if gotField {
		return field.stringValues(), true
	} else {
		return []string{}, false
	}
}

type VSMetaTimespan struct {
	Field []VSMetaField `json:"field"`
	Group []VSMetaGroup `json:"group"`
	Start string        `json:"start"`
	End   string        `json:"end"`
}

func (t VSMetaTimespan) RootFieldForName(fieldName string) (VSMetaField, bool) {
	for _, field := range t.Field {
		if field.Name == fieldName {
			return field, true
		}
	}
	return VSMetaField{}, false
}

func (t VSMetaTimespan) ValueForField(fieldName string) ([]string, bool) {
	field, gotRootField := t.RootFieldForName(fieldName)

	if gotRootField {
		return field.stringValues(), true
	}

	for _, group := range t.Group {
		values, haveField := group.ValueForField(fieldName)
		if haveField {
			return values, true
		}
	}
	return nil, false
}

func (t VSMetaTimespan) FirstValueForField(fieldName string, defaultValue string) string {
	values, hasValue := t.ValueForField(fieldName)
	if hasValue && len(values) > 0 {
		return values[0]
	} else {
		return defaultValue
	}
}

func (t VSMetaTimespan) ValueOrPanic(fieldName string) string {
	values, hasValue := t.ValueForField(fieldName)
	if hasValue && len(values) > 0 {
		return values[0]
	} else {
		panic(fmt.Sprintf("field %s has no value", fieldName))
	}
}

func (t VSMetaTimespan) AllValuesForField(fieldName string, defaultValue []string) []string {
	values, hasValue := t.ValueForField(fieldName)
	if hasValue {
		return values
	} else {
		return defaultValue
	}
}

func (t VSMetaTimespan) GroupForName(groupName string) (VSMetaGroup, bool) {
	for _, group := range t.Group {
		if group.Name == groupName {
			return group, true
		}
	}
	return VSMetaGroup{}, false
}

type VSMetadata struct {
	Revision string           `json:"revision"`
	Group    []string         `json:"group"` //root group name(s)
	Timespan []VSMetaTimespan `json:"timespan"`
}

func (m *VSMetadata) DefaultTimespan() (VSMetaTimespan, bool) {
	for _, ts := range m.Timespan {
		if ts.Start == "-INF" && ts.End == "+INF" {
			return ts, true
		}
	}
	return VSMetaTimespan{}, false
}

func (m *VSMetadata) GetStatus() (string, bool) {
	defaultTs, hasTs := m.DefaultTimespan()
	if !hasTs {
		return "", false
	}

	statusValues, hasStatus := defaultTs.ValueForField("gnm_asset_status")
	if !hasStatus || len(statusValues) == 0 {
		return "", false
	}

	if len(statusValues) > 1 {
		log.Printf("WARNING multiple status values present: %v. Using the first", statusValues)
	}

	return statusValues[0], true
}

func (m *VSMetadata) IsExternalArchive() bool {
	statusValue, _ := m.GetStatus()
	return statusValue == "Archived to External"
}
