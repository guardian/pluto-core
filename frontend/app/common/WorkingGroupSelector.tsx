import React, { useState, useEffect } from "react";
import axios from "axios";
import WorkingGroup from "../WorkingGroups/WorkingGroup";
import {
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from "@material-ui/core";
import ErrorIcon from "@material-ui/icons/Error";
import { useGuardianStyles } from "~/misc/utils";

interface WorkingGroupSelectorProps {
  workingGroupId: number;
  onChange: (evt: React.ChangeEvent<HTMLSelectElement>) => void;
  maxLength?: number;
  showCommissioner?: boolean;
}

const WorkingGroupSelector: React.FC<WorkingGroupSelectorProps> = (props) => {
  const [workingGroupList, setWorkingGroupList] = useState<
    WorkingGroup[] | undefined
  >(undefined);
  const [loading, setLoading] = useState<boolean>(true);
  const [lastError, setLastError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [commissionerName, setCommissionerName] = useState<string>("");

  const classes = useGuardianStyles();

  /**
   * validate if the user is actually allowed to select this value. If the 'hide' property is true
   * then the group is discontinued and you should not put anything in
   * @param evt
   */
  const validateChangedValue = (evt: any) => {
    try {
      const newWgId = parseInt(evt.target.value);
      const newWg = workingGroupList
        ? workingGroupList.find((entry) => entry.id == newWgId)
        : undefined;
      if (newWg?.hide) {
        setValidationError(
          `${newWg.name} is discontinued, please select an active group`
        );
      } else {
        props.onChange(evt);
        if (newWg) setCommissionerName(newWg.commissioner);
      }
    } catch (err) {
      console.error(err);
      setValidationError("Could not validate: " + err.toString());
    }
  };

  useEffect(() => {
    console.log("Working group list or id updated", props.workingGroupId);
    const newWg = workingGroupList
      ? workingGroupList.find((entry) => entry.id == props.workingGroupId)
      : undefined;
    if (newWg) setCommissionerName(newWg.commissioner);
  }, [props.workingGroupId, workingGroupList]);

  useEffect(() => {
    axios
      .get(`/api/pluto/workinggroup?length=${props.maxLength ?? 500}`)
      .then((result) => {
        setWorkingGroupList(result.data.result as WorkingGroup[]);
        setLoading(false);
        setLastError(null);
      })
      .catch((err) => {
        setLoading(false);
        console.error("Could not load working groups: ", err);
        setLastError("Could not load. See browser console for details.");
      });
  }, []);

  return (
    <>
      {loading ? <CircularProgress style={{ maxWidth: "16px" }} /> : null}
      {lastError ? (
        <Typography className="error">
          <ErrorIcon />
          {lastError}
        </Typography>
      ) : null}
      {workingGroupList ? (
        <FormControl>
          <InputLabel htmlFor="working-group-selector">
            Working Group
          </InputLabel>
          <Select
            id="working-group-selector"
            value={props.workingGroupId}
            onChange={validateChangedValue}
          >
            {workingGroupList
              .sort(
                ({ hide: stateA = false }, { hide: stateB = false }) =>
                  Number(stateA) - Number(stateB)
              )
              .map((entry) => (
                <MenuItem
                  key={entry.id}
                  value={entry.id}
                  className={
                    entry.hide ? classes.discontinuedWG : classes.normalWG
                  }
                >
                  {entry.name} {entry.hide ? "(discontinued)" : ""}
                </MenuItem>
              ))}
          </Select>
          {validationError ? (
            <Typography className={classes.validationError}>
              <ErrorIcon />
              {validationError}
            </Typography>
          ) : null}
        </FormControl>
      ) : null}
      {commissionerName != "" ? (
        <TextField
          id="working-group-commissioner"
          label="Current Commissioner"
          value={commissionerName}
          disabled={true}
        />
      ) : null}
    </>
  );
};

export default WorkingGroupSelector;
