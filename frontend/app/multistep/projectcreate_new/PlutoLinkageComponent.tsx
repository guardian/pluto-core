import React, { useEffect, useState } from "react";
import {
  FormControlLabel,
  Grid,
  Radio,
  RadioGroup,
  Typography,
} from "@material-ui/core";
import WorkingGroupSelector from "../common/WorkingGroupSelectorNew";
import { makeStyles } from "@material-ui/core/styles";
import axios from "axios";
import { SystemNotification, SystemNotifcationKind } from "pluto-headers";
import CommissionSelector from "../common/CommissionSelectorNew";
import { loadWorkingGroups } from "../common/WorkingGroupService";

interface PlutoLinkageComponentProps {
  commissionId?: number;
  workingGroupId?: number;
  commissionIdDidChange: (newValue: number) => void;
  workingGroupIdDidChange: (newValue: number) => void;
}

const useStyles = makeStyles((theme) => ({
  selectorbox: {
    width: "50%",
  },
  warningText: {
    color: theme.palette.warning.dark,
    textAlign: "center",
  },
}));

const PlutoLinkageComponent: React.FC<PlutoLinkageComponentProps> = (props) => {
  const [knownWorkingGroups, setKnownWorkingGroups] = useState<WorkingGroup[]>(
    []
  );
  const [showingStatus, setShowingStatus] = useState<ProjectStatus | "all">(
    "all"
  );

  const classes = useStyles();

  useEffect(() => {
    loadWorkingGroups(setKnownWorkingGroups);
  }, []);

  return (
    <div>
      <Typography variant="h3">Select commission</Typography>
      <Typography>
        We need to know which piece of work this project file relates to. Please
        select the relevant working group and commission.
      </Typography>
      <Grid direction="row" container style={{ marginTop: "0.8em" }}>
        <Grid item className={classes.selectorbox}>
          <Typography variant="h6">Working group</Typography>
          <WorkingGroupSelector
            valueWasSet={props.workingGroupIdDidChange}
            workingGroupList={knownWorkingGroups}
            currentValue={props.workingGroupId}
          />
          {props.workingGroupId === undefined ? (
            <Typography className={classes.warningText}>
              No working group selected yet
            </Typography>
          ) : null}
        </Grid>
        <Grid item className={classes.selectorbox}>
          <Typography variant="h6">Commission</Typography>
          <CommissionSelector
            valueWasSet={props.commissionIdDidChange}
            workingGroupId={props.workingGroupId}
            selectedCommissionId={props.commissionId}
            showStatus={showingStatus}
          />
          {props.commissionId === undefined ? (
            <Typography className={classes.warningText}>
              No commission selected yet
            </Typography>
          ) : null}
          <RadioGroup
            aria-label="Show status"
            name="showing_status"
            value={showingStatus}
            row
            onChange={(evt) =>
              setShowingStatus(evt.target.value as ProjectStatus | "all")
            }
          >
            <FormControlLabel
              value="In Production"
              control={<Radio />}
              label="In Production only"
            />
            <FormControlLabel
              value="New"
              control={<Radio />}
              label="New commissions only"
            />
            <FormControlLabel
              value="all"
              control={<Radio />}
              label="All commissions"
            />
          </RadioGroup>
        </Grid>
      </Grid>
    </div>
  );
};

export default PlutoLinkageComponent;
