import React, { ChangeEvent, useEffect, useState } from "react";
import axios from "axios";
import SelectInput from "@material-ui/core/Select/SelectInput";
import {
  CircularProgress,
  Grid,
  MenuItem,
  Select,
  Typography,
} from "@material-ui/core";

interface PostrunSelectorProps {
  onChange: (newValue: number) => void;
  value?: number;
  id?: string;
}

interface PostrunEntry {
  id: number;
  runnable: string;
  title: string;
  description: string;
  owner: string;
  version: number;
  ctime: string;
}

const PostrunSelector: React.FC<PostrunSelectorProps> = (props) => {
  const [entries, setEntries] = useState<PostrunEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [lastError, setLastError] = useState<string | undefined>(undefined);

  useEffect(() => {
    const doLoad = async () => {
      try {
        const response = await axios.get("/api/postrun");
        setEntries(response.data.result);
        setLoading(false);
        setLastError(undefined);
      } catch (e) {
        console.error("Could not load postruns: ", e);
        setLoading(false);

        if (e.response) {
          setLastError(`Server error ${e.response.status}`);
        } else if (e.request) {
          setLastError("No response from server");
        } else {
          setLastError("Internal error, see browser console");
        }
      }
    };
    doLoad();
  }, []);

  return (
    <Grid container direction="row">
      <Grid item xs={6}>
        <Select
          value={props.value ?? "-1"}
          id={props.id}
          onChange={(evt) => {
            try {
              const numericValue = parseInt(evt.target.value as string);
              props.onChange(numericValue);
            } catch (e) {
              console.error("Could not signal change of postrun value: ", e);
            }
          }}
        >
          <MenuItem value={-1}>Not set</MenuItem>
          {entries.map((ent) => (
            <MenuItem key={ent.id} value={ent.id}>
              {ent.title}
            </MenuItem>
          ))}
        </Select>
      </Grid>
      <Grid item xs={1}>
        {loading ? <CircularProgress /> : null}
      </Grid>
      <Grid item xs={5}>
        {lastError ? (
          <Typography variant="caption" style={{ color: "red", margin: 0 }}>
            {lastError}
          </Typography>
        ) : null}
      </Grid>
    </Grid>
  );
};

export default PostrunSelector;
