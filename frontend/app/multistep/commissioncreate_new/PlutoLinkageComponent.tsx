import React, { useEffect, useState } from "react";
import { Grid, Typography } from "@material-ui/core";
import WorkingGroupSelector from "../common/WorkingGroupSelectorNew";
import { loadWorkingGroups } from "../common/WorkingGroupService";
import { useGuardianStyles } from "~/misc/utils";

interface PlutoLinkageComponentProps {
  workingGroupId?: number;
  workingGroupIdDidChange: (newValue: number) => void;
}

const PlutoLinkageComponent: React.FC<PlutoLinkageComponentProps> = (props) => {
  const [knownWorkingGroups, setKnownWorkingGroups] = useState<WorkingGroup[]>(
    []
  );

  const classes = useGuardianStyles();

  useEffect(() => {
    loadWorkingGroups(setKnownWorkingGroups).catch((err) => {
      console.error("Could not load working groups: ", err);
    });
  }, []);

  return (
    <div className={classes.common_box_size}>
      <Typography variant="h3">Select Working Group</Typography>
      <Typography style={{ textAlign: "center" }}>
        We need to know which working group is undertaking this project.
        <br />
        If you are unsure which to choose, please ask your commissioning editor.
      </Typography>
      <Grid
        direction="row"
        container
        style={{ marginTop: "0.8em" }}
        justifyContent="center"
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
