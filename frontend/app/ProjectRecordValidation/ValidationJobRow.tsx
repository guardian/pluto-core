import React from "react";
import { Grid, IconButton, TableCell, TableRow } from "@material-ui/core";
import { useHistory } from "react-router";
import { ZoomIn, ZoomOut } from "@material-ui/icons";
import { useGuardianStyles } from "~/misc/utils";

interface ValidationJobRowProps {
  data: ValidationJob;
  onUserFilterClicked: (newUserName: string) => void;
  currentUserFilter?: string;
  onStatusFilterClicked: (newFilterStatus: string) => void;
  currentStatusFilter?: string;
}

const ValidationJobRow: React.FC<ValidationJobRowProps> = (props) => {
  const history = useHistory();
  const classes = useGuardianStyles();

  const jumpToValidation = () =>
    history.push(`/validate/project/${props.data.uuid}`);

  return (
    <TableRow onClick={jumpToValidation} className={classes.tableRow}>
      <TableCell>{props.data.uuid}</TableCell>
      <TableCell>{props.data.jobType}</TableCell>
      <TableCell>
        <Grid container>
          <Grid item>
            <IconButton
              onClick={() => props.onUserFilterClicked(props.data.userName)}
            >
              {props.currentUserFilter === props.data.userName ? (
                <ZoomOut />
              ) : (
                <ZoomIn />
              )}
            </IconButton>
          </Grid>
          <Grid item>{props.data.userName}</Grid>
        </Grid>
      </TableCell>
      <TableCell>
        <Grid container>
          <Grid item>
            <IconButton
              onClick={() => props.onStatusFilterClicked(props.data.status)}
            >
              {props.currentStatusFilter === props.data.status ? (
                <ZoomOut />
              ) : (
                <ZoomIn />
              )}
            </IconButton>
          </Grid>
          <Grid item>{props.data.status}</Grid>
        </Grid>
      </TableCell>
      <TableCell>{props.data.startedAt ? props.data.startedAt : ""}</TableCell>
      <TableCell>
        {props.data.completedAt ? props.data.completedAt : ""}
      </TableCell>
      <TableCell>
        {props.data.errorMessage ? props.data.errorMessage : <i>(no errors)</i>}
      </TableCell>
    </TableRow>
  );
};

export default ValidationJobRow;
