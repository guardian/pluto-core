import React from "react";
import { makeStyles } from "@material-ui/core/styles";
import { CircularProgress, Grid, Typography } from "@material-ui/core";
import WarningRoundedIcon from "@material-ui/icons/WarningRounded";

interface InProgressComponentProps {
  didFail: boolean;
  errorMessage?: string;
}

const useStyles = makeStyles((theme) => ({
  centeredContainer: {
    marginLeft: "auto",
    marginRight: "auto",
    width: "400px",
    marginTop: "auto",
    marginBottom: "auto",
    padding: "1em",
  },
  errorIcon: {
    color: theme.palette.warning.dark,
    width: "100px",
    height: "100px",
  },
  warningText: {
    color: theme.palette.warning.dark,
    textAlign: "center",
  },
  regularText: {
    textAlign: "center",
  },
  progressSpinner: {
    width: "100px",
    height: "100px",
  },
}));

const InProgressComponent: React.FC<InProgressComponentProps> = (props) => {
  const classes = useStyles();

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
              Creating your project, please wait...
            </Typography>
          )}
        </Grid>
      </Grid>
    </div>
  );
};

export default InProgressComponent;
