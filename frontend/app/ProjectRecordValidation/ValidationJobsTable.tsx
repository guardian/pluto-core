import React, { useState } from "react";
import {
  Paper,
  TableContainer,
  TableHead,
  Table,
  TableRow,
  TableCell,
  Typography,
  TableSortLabel,
  TableBody,
} from "@material-ui/core";
import ValidationJobRow from "./ValidationJobRow";
import { getComparator, stableSort } from "../TableUtils";
import { useGuardianStyles } from "~/misc/utils";

interface ValidationJobsTableProps {
  data: ValidationJob[];
  onUserFilterClicked: (newUserName: string) => void;
  currentUserFilter?: string;
  onStatusFilterClicked: (newFilterStatus: string) => void;
  currentStatusFilter?: string;
}

const ValidationJobsTable: React.FC<ValidationJobsTableProps> = (props) => {
  const [sortColumn, setSortColumn] = useState<ValidationJobColumn>(
    "startedAt"
  );
  const [sortOrder, setSortOrder] = useState<"asc" | "desc">("desc");

  const classes = useGuardianStyles();

  /**
   * callback which is invoked when the user clicks on a sort label.
   * If the current sort column (at the time of the setSortColumn running) is the same as the column that was clicked,
   * then the direction is toggled to be the opposite of its current value.
   * If the current sort column is different to the column that was clicked, then the sort column is changed but the direction left alone.
   * @param index the column index which was clicked
   */
  const updateSort = (index: ValidationJobColumn) => {
    setSortColumn((prevValue) => {
      if (prevValue == index) {
        setSortOrder((prevValue) => (prevValue === "asc" ? "desc" : "asc"));
        return index;
      } else {
        return index;
      }
    });
  };

  return (
    <Paper elevation={3}>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>
                <Typography className={classes.tableHeaderText}>
                  Job ID
                </Typography>
              </TableCell>
              <TableCell
                sortDirection={sortColumn === "jobType" ? sortOrder : false}
              >
                <TableSortLabel
                  active={sortColumn === "jobType"}
                  direction={sortOrder}
                  onClick={() => updateSort("jobType")}
                >
                  <Typography className={classes.tableHeaderText}>
                    Job Type
                  </Typography>
                </TableSortLabel>
              </TableCell>
              <TableCell
                sortDirection={sortColumn === "userName" ? sortOrder : false}
              >
                <TableSortLabel
                  active={sortColumn === "userName"}
                  direction={sortOrder}
                  onClick={() => updateSort("userName")}
                >
                  <Typography className={classes.tableHeaderText}>
                    Owner
                  </Typography>
                </TableSortLabel>
              </TableCell>
              <TableCell
                sortDirection={sortColumn === "status" ? sortOrder : false}
              >
                <TableSortLabel
                  active={sortColumn === "status"}
                  direction={sortOrder}
                  onClick={() => updateSort("status")}
                >
                  <Typography className={classes.tableHeaderText}>
                    Status
                  </Typography>
                </TableSortLabel>
              </TableCell>
              <TableCell
                sortDirection={sortColumn === "startedAt" ? sortOrder : false}
              >
                <TableSortLabel
                  active={sortColumn === "startedAt"}
                  direction={sortOrder}
                  onClick={() => updateSort("startedAt")}
                >
                  <Typography className={classes.tableHeaderText}>
                    Started at
                  </Typography>
                </TableSortLabel>
              </TableCell>
              <TableCell
                sortDirection={sortColumn === "completedAt" ? sortOrder : false}
              >
                <TableSortLabel
                  active={sortColumn === "completedAt"}
                  direction={sortOrder}
                  onClick={() => updateSort("completedAt")}
                >
                  <Typography className={classes.tableHeaderText}>
                    Completed at
                  </Typography>
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <Typography className={classes.tableHeaderText}>
                  Errors
                </Typography>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {stableSort(props.data, getComparator(sortOrder, sortColumn)).map(
              (row, idx) => (
                <ValidationJobRow
                  data={row}
                  onUserFilterClicked={props.onUserFilterClicked}
                  onStatusFilterClicked={props.onStatusFilterClicked}
                  currentStatusFilter={props.currentStatusFilter}
                  currentUserFilter={props.currentUserFilter}
                  key={idx}
                />
              )
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
};

export default ValidationJobsTable;
