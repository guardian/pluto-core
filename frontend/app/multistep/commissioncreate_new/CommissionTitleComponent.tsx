import React from "react";
import { Grid, Input, TextField, Typography } from "@material-ui/core";
import DateFnsUtils from "@date-io/date-fns";
import {
  MuiPickersUtilsProvider,
  KeyboardTimePicker,
  KeyboardDatePicker,
} from "@material-ui/pickers";
import { useGuardianStyles } from "~/misc/utils";
import ProductionOfficeComponent from "../projectcreate_new/ProductionOfficeComponent";

interface CommissionTitleComponentProps {
  title: string;
  expiration: Date;
  onTitleChanged: (newValue: string) => void;
  onExpirationChanged: (newValue: Date) => void;
  valueWasSet: (newValue: ProductionOffice) => void;
  productionOfficeValue: string;
}

const CommissionTitleComponent: React.FC<CommissionTitleComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();

  return (
    <div className={classes.common_box_size}>
      <MuiPickersUtilsProvider utils={DateFnsUtils}>
        <Typography variant="h3">Commission Configuration</Typography>
        {/* <Typography>
          Now, we need a descriptive name for your new commission and we need to
          know when it is scheduled to be completed.
        </Typography>
        <Typography>
          This value can be updated at any time in the future, so don't worry
          too much about setting something very long now.
          <br />
          Beware, if you set a value too far in the future, it is possible that
          at some point in the future MM Tech will conclude that your commission
          was actually finished long ago and archive it; so please try to be
          accurate.
        </Typography> */}
        <Grid container direction="column">
          <Grid item xs={6} sm={6}>
            <TextField
              style={{ width: "100%" }}
              label="Commission title"
              placeholder="Commission title"
              helperText="Enter a good descriptive Commission titlee"
              margin="normal"
              id="commissionNameInput"
              onChange={(event) => props.onTitleChanged(event.target.value)}
              value={props.title}
            />
          </Grid>
          <Grid item xs={6} sm={6}>
            <KeyboardDatePicker
              className={classes.inputBox}
              disableToolbar
              variant="inline"
              format="dd/MM/yyyy"
              margin="normal"
              label="Scheduled completion"
              helperText="This value can be updated at any time in the future, so don't worry
                  too much about setting something very long now."
              value={props.expiration}
              onChange={(newValue) =>
                newValue ? props.onExpirationChanged(newValue) : undefined
              }
            />
          </Grid>
          <Grid item xs={12}>
            <ProductionOfficeComponent
              valueWasSet={props.valueWasSet}
              productionOfficeValue={props.productionOfficeValue}
            />
          </Grid>
        </Grid>
        {/* <table>
          <tbody>
            <tr>
              <td>Commission name</td>
              <td>
                <Input
                  className={classes.inputBox}
                  id="commissionNameInput"
                  onChange={(evt) => props.onTitleChanged(evt.target.value)}
                  value={props.title}
                />
              </td>
            </tr>
            <tr>
              <td>Scheduled Completion</td>
              <td>
                <KeyboardDatePicker
                  className={classes.inputBox}
                  disableToolbar
                  variant="inline"
                  format="dd/MM/yyyy"
                  margin="normal"
                  label="Scheduled completion"
                  value={props.expiration}
                  onChange={(newValue) =>
                    newValue ? props.onExpirationChanged(newValue) : undefined
                  }
                />
              </td>
            </tr>
          </tbody>
        </table> */}
      </MuiPickersUtilsProvider>
    </div>
  );
};

export default CommissionTitleComponent;
