import React, { useEffect, useState } from "react";
import { Grid, Typography } from "@material-ui/core";
import WorkingGroupSelector from "../common/WorkingGroupSelectorNew";
import { makeStyles } from "@material-ui/core/styles";
import axios from "axios";
import { SystemNotifcationKind, SystemNotification } from "pluto-headers";
import { loadWorkingGroups } from "../common/WorkingGroupService";

interface PlutoLinkageComponentProps {
  workingGroupId?: number;
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

  const classes = useStyles();

  useEffect(() => {
    loadWorkingGroups(setKnownWorkingGroups).catch((err) => {
      console.error("Could not load working groups: ", err);
    });
  }, []);

  return (
    <div>
      <Typography variant="h3">Select Working Group</Typography>
      <Typography>
        We need to know which working group is undertaking this project. If you
        are unsure which to choose, please ask your commissioning editor.
      </Typography>
      <Grid
        direction="row"
        container
        style={{ marginTop: "0.8em" }}
        justify="center"
      >
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
      </Grid>
    </div>
  );
};

export default PlutoLinkageComponent;
