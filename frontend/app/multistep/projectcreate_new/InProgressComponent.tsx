import React from "react";
import { CircularProgress, Grid, Typography } from "@material-ui/core";
import WarningRoundedIcon from "@material-ui/icons/WarningRounded";
import { useGuardianStyles } from "~/misc/utils";

interface InProgressComponentProps {
  didFail: boolean;
  errorMessage?: string;
  description: string;
}

const InProgressComponent: React.FC<InProgressComponentProps> = (props) => {
  const classes = useGuardianStyles();

  return (
    <div className={classes.centeredContainer}>
      <Grid container direction="column" alignItems="center">
        <Grid item>
          {props.didFail ? (
            <WarningRoundedIcon className={classes.errorIcon} />
          ) : (
            <CircularProgress
              size="100px"
              className={classes.progressSpinner}
            />
          )}
        </Grid>
        <Grid item>
          {props.didFail ? (
            <Typography className={classes.warningText}>
              {props.errorMessage ?? "Could not create project"}
            </Typography>
          ) : (
            <Typography className={classes.regularText}>
              {props.description}
            </Typography>
          )}
        </Grid>
      </Grid>
    </div>
  );
};

export default InProgressComponent;
