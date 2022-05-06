import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import {
  Grid,
  IconButton,
  LinearProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  Typography,
} from "@material-ui/core";
import { Alert } from "@material-ui/lab";
import { differenceInMinutes, format, parseISO } from "date-fns";
import ValidationTableRow from "./ValidationTableRow";
import { useHistory } from "react-router";
import { ArrowBackRounded } from "@material-ui/icons";
import { Helmet } from "react-helmet";
import { useGuardianStyles } from "~/misc/utils";

interface ValidationJobResultsLocationParams {
  jobId: string;
}

type SortColumns = "job-id" | "item-id" | "detection-time";
type SortOrders = "asc" | "desc";
const ValidationJobResults: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [totalProblemReports, setTotalProblemReports] = useState(0);
  const [problemReports, setProblemReports] = useState<ValidationProblem[]>([]);
  const [jobDetails, setJobDetails] = useState<ValidationJob | undefined>(
    undefined
  );
  const [lastError, setLastError] = useState<string | undefined>(undefined);

  const [currentPageNumber, setCurrentPageNumber] = useState(0);
  const [currentRowsPerPage, setCurrentRowsPerPage] = useState(10);
  const rowsPerPageOptions = [10, 25, 50, 75, 100];

  const [sortColumn, setSortColumn] = useState<SortColumns>("detection-time");
  const [sortOrder, setSortOrder] = useState<SortOrders>("desc");

  const routerParams = useParams<ValidationJobResultsLocationParams>();
  const history = useHistory();

  const classes = useGuardianStyles();

  const changeRowsPerPage = (
    evt: React.ChangeEvent<HTMLTextAreaElement | HTMLInputElement>
  ) => {
    setCurrentRowsPerPage(parseInt(evt.target.value, 10));
    setCurrentPageNumber(0);
  };

  const tablePageChanged = (
    evt: React.MouseEvent<HTMLButtonElement> | null,
    page: number
  ) => {
    setCurrentPageNumber(page);
  };

  useEffect(() => {
    refreshData();
  }, [currentPageNumber, currentRowsPerPage, sortColumn, sortOrder]);

  const refreshData = async () => {
    setLoading(true);
    try {
      const jobDetailsResponse = await axios.get<ValidationJob>(
        `/api/validation/${routerParams.jobId}`,
        { validateStatus: (status) => status === 200 || status === 404 }
      );
      if (jobDetailsResponse.status === 404) {
        setLastError("There is no job with this ID");
        setLoading(false);
      } else {
        setJobDetails(jobDetailsResponse.data);
        const startAt = currentPageNumber * currentRowsPerPage;

        const response = await axios.get<ValidationProblemListResponse>(
          `/api/validation/${routerParams.jobId}/faults?from=${startAt}&limit=${currentRowsPerPage}&sortColumn=${sortColumn}&sortOrder=${sortOrder}`
        );
        setLastError(undefined);
        setProblemReports(response.data.entries);
        setLoading(false);
        setTotalProblemReports(response.data.totalCount);
      }
    } catch (err) {
      console.error(err);
      setLastError(err.toString());
      setLoading(false);
    }
  };

  /**
   * returns a UI string representing the time that the job has been in progress
   */
  const getElapsedTime = () => {
    const maybeStartTime = jobDetails?.startedAt
      ? parseISO(jobDetails.startedAt)
      : undefined;
    const maybeCompletedTime = jobDetails?.completedAt
      ? parseISO(jobDetails.completedAt)
      : undefined;

    try {
      if (maybeStartTime && maybeCompletedTime) {
        const duration = differenceInMinutes(
          maybeCompletedTime,
          maybeStartTime
        );
        if (duration > 60) {
          const hrs = Math.floor(duration);
          const remainingMins = Math.floor(duration - hrs * 60);
          return `Completed in ${hrs} hours and ${remainingMins} mins`;
        } else {
          return `Completed in ${duration} minutes`;
        }
      } else if (maybeStartTime) {
        const duration = differenceInMinutes(Date.now(), maybeStartTime);
        if (duration > 60) {
          const hrs = Math.floor(duration);
          const remainingMins = Math.floor(duration - hrs * 60);
          return `Running for ${hrs} hours and ${remainingMins} mins`;
        } else {
          return `Running for ${duration} minutes`;
        }
      } else {
        return "Not started yet";
      }
    } catch (err) {
      console.error("Could not parse times from ", jobDetails);
      console.error("Error was ", err);
      return "Invalid data";
    }
  };

  const formatStartTime = () => {
    if (jobDetails?.startedAt) {
      try {
        const parsedTime = parseISO(jobDetails.startedAt);
        return format(parsedTime, "E do MMM yyyy HH:mm:ss xx");
      } catch (err) {
        console.error(
          "Could not parse and format ",
          jobDetails.startedAt,
          ": ",
          err
        );
        return "Invalid data";
      }
    } else {
      return "Not started yet";
    }
  };

  const columnClicked = (col: SortColumns) => {
    setSortColumn((prevSortColumn) => {
      if (prevSortColumn === col) {
        //if we are clicking on the column already selected, change the sort order
        setSortOrder((prevState) => (prevState === "asc" ? "desc" : "asc"));
        return col;
      } else {
        //otherwise change the column
        return col;
      }
    });
  };

  return (
    <>
      <Helmet>
        <title>Project validation results</title>
      </Helmet>
      {loading ? <LinearProgress style={{ width: "100%" }} /> : undefined}
      <Typography variant="h2">Validate project files</Typography>

      {lastError ? <Alert severity="error">{lastError}</Alert> : undefined}

      <Paper elevation={3}>
        {jobDetails ? (
          <Grid container spacing={5} className={classes.infoBanner}>
            <Grid item>
              <IconButton onClick={() => history.push("/validate/project")}>
                <ArrowBackRounded />
              </IconButton>
            </Grid>
            <Grid item>
              <Typography className={classes.headerTitle}>Job ID</Typography>
              <Typography>{jobDetails.uuid}</Typography>
            </Grid>
            <Grid item>
              <Typography className={classes.headerTitle}>Scan type</Typography>
              <Typography>{jobDetails.jobType}</Typography>
            </Grid>
            <Grid item>
              <Typography className={classes.headerTitle}>Run by</Typography>
              <Typography>{jobDetails.userName}</Typography>
            </Grid>
            <Grid item>
              <Typography className={classes.headerTitle}>
                Current status
              </Typography>
              <Typography>{jobDetails.status}</Typography>
            </Grid>
            <Grid item>
              <Typography className={classes.headerTitle}>
                Started at
              </Typography>
              <Typography>{formatStartTime()}</Typography>
            </Grid>
            <Grid item>
              <Typography className={classes.headerTitle}>
                Elapsed time
              </Typography>
              <Typography>{getElapsedTime()}</Typography>
            </Grid>
            <Grid item>
              <Typography className={classes.headerTitle}>
                Total faults found
              </Typography>
              <Typography>{totalProblemReports}</Typography>
            </Grid>
          </Grid>
        ) : undefined}
      </Paper>

      <Paper elevation={3}>
        <TableContainer>
          <Table size="small" className={classes.resultsTable}>
            <TableHead>
              <TableRow>
                <TableCell>
                  <TableSortLabel
                    active={sortColumn === "detection-time"}
                    direction={
                      sortColumn === "detection-time" ? sortOrder : "asc"
                    }
                    onClick={() => columnClicked("detection-time")}
                  >
                    Problem detected at
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortColumn === "item-id"}
                    direction={sortColumn === "item-id" ? sortOrder : "asc"}
                    onClick={() => columnClicked("item-id")}
                  >
                    Affected item
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <Typography>Details</Typography>
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {problemReports.map((entry, idx) => (
                <ValidationTableRow data={entry} key={idx} />
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          count={totalProblemReports}
          component="div"
          classes={{ root: classes.fullWidth }}
          page={currentPageNumber}
          onPageChange={tablePageChanged}
          rowsPerPage={currentRowsPerPage}
          onRowsPerPageChange={changeRowsPerPage}
          rowsPerPageOptions={rowsPerPageOptions}
        />
      </Paper>
    </>
  );
};

export default ValidationJobResults;
