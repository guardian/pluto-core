import React, { ChangeEvent, useEffect, useMemo, useState } from "react";
import { Box, Grid, TextField, makeStyles } from "@material-ui/core";
import { Autocomplete, Alert } from "@material-ui/lab";
import axios from "axios";
import { Link } from "react-router-dom";

interface ObituarySelectorProps {
  valueDidChange: (event: ChangeEvent<{}>, newValue: string | null) => void;
  value: string;
  label?: string;
  shouldValidate?: boolean;
}

const useStyles = makeStyles((theme) => ({
  root: {
    position: "relative",
  },
  alert: {
    backgroundColor: theme.palette.background.paper,
    position: "absolute",
    top: -90,
    left: -15,
    right: 0,
    zIndex: 1, // to ensure the alert appears on top
  },
}));

/**
 * This component provides an auto-complete for known obituaries.
 */
const ObituarySelector: React.FC<ObituarySelectorProps> = (props) => {
  const [obituaryOptions, setObituaryOptions] = useState<null | string[]>(null);
  const [inputValue, setInputValue] = useState("");
  const [projectId, setProjectId] = useState<number | null>(null);
  const [validationFailed, setValidationFailed] = useState(false);

  const classes = useStyles();
  /**
   * useMemo remembers a value for a given input and will not-recalculate it if the dependency does not change
   * the factory function here performs a lookup for a prefix of `inputValue`.
   * the promise rejects on failure
   */
  const searchObits = useMemo(() => {
    const prefixString =
      inputValue != "" ? `?prefix=${encodeURIComponent(inputValue)}` : "";
    return async () => {
      const response = await axios.get<{ obitNames: string[] }>(
        `/api/obits/names${prefixString}`
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
      .then((obitNames) => {
        let obitNamesTitleCase: string[] = [];
        obitNames.map((name) => obitNamesTitleCase.push(toTitleCase(name)));
        setObituaryOptions(obitNamesTitleCase);
      })
      .catch((err) => {
        console.error(`Could not get obituary names list: ${err}`);
        if (err.response) console.log(err.response.data);
      });
  }, [inputValue, props.value, searchObits]);

  const validateEntry = async (newValue: string) => {
    const exists = contains(obituaryOptions, newValue);
    if (exists) {
      const response = await axios.get(
        `/api/project/obits?name=${encodeURIComponent(newValue.toLowerCase())}`
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

  function toTitleCase(str: string) {
    return str.replace(/\w\S*/g, function (txt) {
      return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
    });
  }

  return (
    <Grid
      container
      direction="column"
      alignItems="stretch"
      spacing={1}
      className={classes.root}
    >
      <Grid item xs={12}>
        <Box minWidth={"400px"} width={"100%"} display="flex">
          {inputValue != "" ? (
            <Autocomplete
              style={{ width: "100%" }}
              freeSolo
              autoComplete
              includeInputInList
              value={toTitleCase(props.value)}
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
          ) : (
            <Autocomplete
              style={{ width: "100%" }}
              freeSolo
              autoComplete
              includeInputInList
              value={toTitleCase(props.value)}
              onChange={props.valueDidChange}
              onInputChange={inputDidChange}
              options={[]}
              renderInput={(params) => (
                <TextField
                  {...params}
                  error={validationFailed}
                  label={props.label}
                />
              )}
            />
          )}
        </Box>
      </Grid>
      {validationFailed && props.shouldValidate && (
        <Grid item xs={12}>
          <Alert
            className={classes.alert}
            variant="outlined"
            severity="error"
            action={
              projectId && (
                <Link to={`/project/${projectId}`}>Edit obituary</Link>
              )
            }
          >
            An obituary project already exists for this person.
            <br />
            If you would like to edit that instead please click
            <br />
            on the link on the right, otherwise click NEXT
            <br />
            to create a new project for this person.
          </Alert>
        </Grid>
      )}
    </Grid>
  );
};

export default ObituarySelector;
