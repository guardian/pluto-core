import React, { ChangeEvent, useEffect, useMemo, useState } from "react";
import { TextField } from "@material-ui/core";
import { Autocomplete } from "@material-ui/lab";
import axios from "axios";

interface UsersAutoCompleteProps {
  valueDidChange: (event: ChangeEvent<{}>, newValue: string[] | null) => void;
  value: string;
  label?: string;
  shouldValidate?: boolean;
}

/**
 * This component provides an auto-complete for known project owners
 */
const UsersAutoComplete: React.FC<UsersAutoCompleteProps> = (props) => {
  const [userOptions, setUserOptions] = useState<string[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [validationFailed, setValidationFailed] = useState(false);
  const [userFieldValue, setUserFieldValue] = useState<string[]>([]);

  useEffect(() => {
    setUserFieldValue(props.value.split("|"));
  }, [props.value]);

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

  const validateEntry = async (newValue: string) => {
    try {
      const response = await axios.get<{ known: boolean }>(
        `/api/known-user?uname=${encodeURIComponent(newValue)}`
      );
      setValidationFailed(!response.data.known);
    } catch (err) {
      console.error(`Could not validate user ${newValue}: `, err);
    }
  };

  const inputDidChange = (evt: ChangeEvent<{}>, newValue: string | null) => {
    setInputValue(newValue ?? "");
  };

  const keyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key == "Enter") {
      if (props.shouldValidate) {
        validateEntry(inputValue);
      }
    }
  };

  return (
    <Autocomplete
      multiple
      freeSolo
      autoComplete
      includeInputInList
      value={userFieldValue}
      //onChange is fired when an option is selected
      onChange={props.valueDidChange}
      //onInputChange is fired when the user types
      onInputChange={inputDidChange}
      onKeyDown={keyPress}
      options={userOptions}
      renderInput={(params) => (
        <TextField
          {...params}
          error={validationFailed}
          helperText={
            validationFailed
              ? "This name is not recognised, please check your typing. If you press the 'ENTER' key this value will be saved and recognised next time"
              : ""
          }
          label={props.label}
        />
      )}
    />
  );
};

export default UsersAutoComplete;
