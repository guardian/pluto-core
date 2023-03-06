package vidispine

type KeyValuePair struct {
	Key   string `json:"key"`
	Value string `json:"value"`
}

type JobStepBrief struct {
	Description *string `json:"description"`
	Number      *int16  `json:"number"`
	Status      string  `json:"status"`
}

type VidispineJob struct {
	JobId       string         `json:"jobId"`
	User        string         `json:"user"`
	Started     *string        `json:"started"`
	Finished    *string        `json:"finished"`
	Status      string         `json:"status"` //check about making this enum
	Type        string         `json:"type"`
	Priority    string         `json:"priority"`
	CurrentStep JobStepBrief   `json:"currentStep"`
	Data        []KeyValuePair `json:"data"`
	TotalSteps  int16          `json:"totalSteps"`
}

func (j *VidispineJob) GetDataValue(forKey string) (string, bool) {
	for _, kv := range j.Data {
		if kv.Key == forKey {
			return kv.Value, true
		}
	}
	return "", false
}

func (j *VidispineJob) GetItemId() (string, bool) {
	return j.GetDataValue("item")
}
