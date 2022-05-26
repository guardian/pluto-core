import React, { ChangeEvent, useEffect, useMemo, useState } from "react";
import { Box, Grid, TextField } from "@material-ui/core";
import { Autocomplete, Alert } from "@material-ui/lab";
import axios from "axios";
import { Link } from "react-router-dom";

interface ObituarySelectorProps {
  valueDidChange: (event: ChangeEvent<{}>, newValue: string | null) => void;
  value: string;
  label?: string;
  shouldValidate?: boolean;
}

/**
 * This component provides an auto-complete for known obituaries.
 */
const ObituarySelector: React.FC<ObituarySelectorProps> = (props) => {
  const [obituaryOptions, setObituaryOptions] = useState<null | string[]>(null);
  const [inputValue, setInputValue] = useState("");
  const [projectId, setProjectId] = useState<number | null>(null);
  const [validationFailed, setValidationFailed] = useState(false);

  /**
   * useMemo remembers a value for a given input and will not-recalculate it if the dependency does not change
   * the factory function here performs a lookup for a prefix of `inputValue`.
   * the promise rejects on failure
   */
  const searchObits = useMemo(() => {
    return async () => {
      const response = await axios.get<{ obitNames: string[] }>(
        `/api/obits/names`
      );
      return response.data.obitNames;
    };
  }, [inputValue]);

  const contains = (arr: string[] | null, q: string) => {
    if (!arr) {
      return;
    }
    return arr.find(
      (item) => q.toString().toLowerCase() === item.toString().toLowerCase()
    );
  };

  /**
   * this effect is called when the user types something and is used to make the server request via a memoized callback
   */
  useEffect(() => {
    searchObits()
      .then((obitNames) => setObituaryOptions(obitNames))
      .catch((err) => {
        console.error(`Could not get obituary names list: ${err}`);
        if (err.response) console.log(err.response.data);
      });
  }, [inputValue, props.value, searchObits]);

  const validateEntry = async (newValue: string) => {
    const exists = contains(obituaryOptions, newValue);
    if (exists) {
      const response = await axios.get(
        `/api/project/obits?name=${encodeURIComponent(newValue)}`
      );
      const data = response?.data;
      const project = data.result[0];

      if (project) {
        setProjectId(project.id);
      }
      setValidationFailed(true);
    }
  };

  const inputDidChange = (evt: ChangeEvent<{}>, newValue: string | null) => {
    setProjectId(null);
    setValidationFailed(false);
    if (props.shouldValidate && newValue) validateEntry(newValue);
    setInputValue(newValue ?? "");
    props.valueDidChange(evt, newValue);
  };

  return (
    <Grid container direction="column" alignItems="stretch" spacing={2}>
      <Grid item xs>
        <Box minWidth={"400px"} width={"100%"} display="flex">
          <Autocomplete
            style={{ width: "100%" }}
            freeSolo
            autoComplete
            includeInputInList
            value={props.value}
            //onChange is fired when an option is selected
            onChange={props.valueDidChange}
            //onInputChange is fired when the user types
            onInputChange={inputDidChange}
            options={obituaryOptions ?? []}
            renderInput={(params) => (
              <TextField
                {...params}
                error={validationFailed}
                label={props.label}
              />
            )}
          />
        </Box>
      </Grid>
      {validationFailed && props.shouldValidate && (
        <Grid item xs>
          <Alert
            variant="outlined"
            severity="error"
            action={
              projectId && (
                <Link to={`/project/${projectId}`}>Edit obituary</Link>
              )
            }
          >
            This name already exists in an obituary project. <br />
            Edit that instead?
          </Alert>
        </Grid>
      )}
    </Grid>
  );
};

export default ObituarySelector;
