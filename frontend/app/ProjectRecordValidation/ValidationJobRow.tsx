import React from "react";
import { makeStyles, TableCell, TableRow } from "@material-ui/core";
import { useHistory } from "react-router";

interface ValidationJobRowProps {
  data: ValidationJob;
}

const useStyles = makeStyles({
  tableRow: {
    cursor: "pointer",
  },
});

const ValidationJobRow: React.FC<ValidationJobRowProps> = (props) => {
  const history = useHistory();
  const classes = useStyles();

  const jumpToValidation = () =>
    history.push(`/validate/project/${props.data.uuid}`);

  return (
    <TableRow onClick={jumpToValidation} className={classes.tableRow}>
      <TableCell>{props.data.uuid}</TableCell>
      <TableCell>{props.data.jobType}</TableCell>
      <TableCell>{props.data.userName}</TableCell>
      <TableCell>{props.data.status}</TableCell>
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
