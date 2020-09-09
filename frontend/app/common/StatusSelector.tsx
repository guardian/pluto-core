import React from "react";
import {
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
} from "@material-ui/core";
import { validProjectStatuses } from "../utils/constants";

interface StatusSelectorProps {
  value: string;
  onChange: (evt: any) => void;
}

const StatusSelector: React.FC<StatusSelectorProps> = (props) => {
  return (
    <FormControl>
      <InputLabel id="label-status">Status</InputLabel>
      <Select
        labelId="label-status"
        value={props.value}
        onChange={props.onChange}
      >
        {validProjectStatuses.map((status) => (
          <MenuItem key={status} value={status}>
            {status}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
};

export default StatusSelector;
