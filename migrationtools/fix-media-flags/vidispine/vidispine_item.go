package vidispine

type VidispineItem struct {
	Shapes   []VidispineShape `json:"shape"`
	Metadata VSMetadata       `json:"metadata"`
	ItemId   string           `json:"id"`
}

type VidispineCollection struct {
	Id       string     `json:"id"`
	Name     string     `json:"name"`
	Metadata VSMetadata `json:"metadata"`
}
