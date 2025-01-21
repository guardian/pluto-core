import React, { useEffect, useState } from "react";
import { Grid, Input, Paper, TextField, Typography } from "@material-ui/core";
import DateFnsUtils from "@date-io/date-fns";
import {
  MuiPickersUtilsProvider,
  KeyboardTimePicker,
  KeyboardDatePicker,
} from "@material-ui/pickers";
import { useGuardianStyles } from "~/misc/utils";
import ProductionOfficeComponent from "../projectcreate_new/ProductionOfficeComponent";
import WorkingGroupSelector from "../common/WorkingGroupSelector";
import { loadWorkingGroups } from "../common/WorkingGroupService";
import { Autocomplete } from "@material-ui/lab";
import {
  SystemNotifcationKind,
  SystemNotification,
} from "@guardian/pluto-headers";

interface CommissionTitleComponentProps {
  title: string;
  expiration: Date;
  onTitleChanged: (newValue: string) => void;
  onExpirationChanged: (newValue: Date) => void;
  valueWasSet: (newValue: ProductionOffice) => void;
  productionOfficeValue: string;
  workingGroupId?: number;
  workingGroupIdDidChange: (newValue: number) => void;
}

const CommissionTitleComponent: React.FC<CommissionTitleComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();
  const [knownWorkingGroups, setKnownWorkingGroups] = useState<WorkingGroup[]>(
    []
  );
  useEffect(() => {
    loadWorkingGroups(setKnownWorkingGroups).catch((err) => {
      console.error("Could not load working groups: ", err);
    });
  }, []);

  return (
    <div className={classes.common_box_size}>
      <MuiPickersUtilsProvider utils={DateFnsUtils}>
        <Typography variant="h3">Commission Configuration</Typography>
        <Grid container direction="column">
          <Paper className={classes.paperWithPadding}>
            <Grid item xs={6} sm={6}>
              <TextField
                style={{ width: 340 }}
                label="Commission Title"
                placeholder="My commission title"
                helperText="Enter a good descriptive Commission title"
                margin="normal"
                id="commissionNameInput"
                onChange={(event) => {
                  props.onTitleChanged(event.target.value.replace(/[_]/g, ""));
                  if (event.target.value.includes("_")) {
                    SystemNotification.open(
                      SystemNotifcationKind.Warning,
                      "Underscores should not be used in commission titles."
                    );
                  }
                }}
                value={props.title}
                FormHelperTextProps={{ style: { fontSize: "0.86rem" } }}
                InputLabelProps={{
                  shrink: true,
                  style: { fontSize: "1.2rem" },
                }}
              />
            </Grid>
          </Paper>
          <Paper className={classes.paperWithPadding}>
            <Grid item xs={6} sm={6}>
              <KeyboardDatePicker
                className={classes.inputBox}
                style={{ width: 340 }}
                disableToolbar
                variant="inline"
                format="dd/MM/yyyy"
                margin="normal"
                label="Scheduled completion"
                helperText="This value can be updated at any time in the future."
                value={props.expiration}
                onChange={(newValue) =>
                  newValue ? props.onExpirationChanged(newValue) : undefined
                }
                FormHelperTextProps={{ style: { fontSize: "0.86rem" } }}
                InputLabelProps={{
                  shrink: true,
                  style: { fontSize: "1.2rem" },
                }}
              />
            </Grid>
          </Paper>
          <Paper className={classes.paperWithPadding}>
            <Grid item xs={12}>
              <ProductionOfficeComponent
                valueWasSet={props.valueWasSet}
                productionOfficeValue={props.productionOfficeValue}
              />
            </Grid>
          </Paper>
          <Paper className={classes.paperWithPadding}>
            <Grid item>
              <Autocomplete
                options={knownWorkingGroups}
                getOptionLabel={(option) => option.name}
                renderInput={(params) => (
                  <TextField
                    style={{ width: 340 }}
                    {...params}
                    label="Working Group"
                    placeholder="Start typing category name"
                    InputLabelProps={{
                      shrink: true,
                      style: { fontSize: "1.2rem" },
                    }}
                  />
                )}
                onChange={(event, newValue) => {
                  if (newValue) {
                    props.workingGroupIdDidChange(newValue.id);
                  }
                }}
                PaperComponent={({ children, ...other }) => (
                  <Paper {...other}>{children}</Paper>
                )}
              />
            </Grid>
          </Paper>
        </Grid>
      </MuiPickersUtilsProvider>
    </div>
  );
};

export default CommissionTitleComponent;
