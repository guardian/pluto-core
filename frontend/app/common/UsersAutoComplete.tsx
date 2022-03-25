import React, { ChangeEvent, useEffect, useMemo, useState } from "react";
import { TextField } from "@material-ui/core";
import { Autocomplete } from "@material-ui/lab";
import axios from "axios";

interface UsersAutoCompleteProps {
  valueDidChange: (event: ChangeEvent<{}>, newValue: string | null) => void;
  value: string;
  label?: string;
}

/**
 * This component provides an auto-complete for known project owners
 */
const UsersAutoComplete: React.FC<UsersAutoCompleteProps> = (props) => {
  const [userOptions, setUserOptions] = useState<string[]>([]);
  const [inputValue, setInputValue] = useState("");

  /**
   * useMemo remembers a value for a given input and will not-recalculate it if the dependency does not change
   * the factory function here performs a lookup for a prefix of `inputValue`.
   * the promise rejects on failure
   */
  const searchUsers = useMemo(() => {
    return async () => {
      const response = await axios.get<{ users: string[] }>(
        `/api/valid-users?prefix=${encodeURIComponent(inputValue)}`
      );
      return response.data.users;
    };
  }, [inputValue]);

  /**
   * this effect is called when the user types something and is used to make the server request via a memoized callback
   */
  useEffect(() => {
    searchUsers()
      .then((users) => setUserOptions(users))
      .catch((err) => {
        console.error(`Could not get valid users list: ${err}`);
        if (err.response) console.log(err.response.data);
      });
  }, [inputValue, props.value, searchUsers]);

  return (
    <Autocomplete
      freeSolo
      autoComplete
      includeInputInList
      value={props.value}
      //onChange is fired when an option is selected
      onChange={props.valueDidChange}
      //onInputChange is fired when the user types
      onInputChange={(evt, newInputValue) => setInputValue(newInputValue)}
      options={userOptions}
      renderInput={(params) => <TextField {...params} label={props.label} />}
    />
  );
};

export default UsersAutoComplete;
