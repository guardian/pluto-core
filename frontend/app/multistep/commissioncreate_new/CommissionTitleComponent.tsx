import React from "react";
import { Input, Typography } from "@material-ui/core";
import DateFnsUtils from "@date-io/date-fns";
import {
  MuiPickersUtilsProvider,
  KeyboardTimePicker,
  KeyboardDatePicker,
} from "@material-ui/pickers";
import { useGuardianStyles } from "~/misc/utils";

interface CommissionTitleComponentProps {
  title: string;
  expiration: Date;
  onTitleChanged: (newValue: string) => void;
  onExpirationChanged: (newValue: Date) => void;
}

const CommissionTitleComponent: React.FC<CommissionTitleComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();

  return (
    <div className={classes.common_box_size}>
      <MuiPickersUtilsProvider utils={DateFnsUtils}>
        <Typography variant="h3">Name your commission</Typography>
        <Typography>
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
        </Typography>
        <table>
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
        </table>
      </MuiPickersUtilsProvider>
    </div>
  );
};

export default CommissionTitleComponent;
