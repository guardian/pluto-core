import React, { useEffect, useState } from "react";
import { useLocation, useParams } from "react-router-dom";
import axios from "axios";
import {
  Grid,
  IconButton,
  LinearProgress,
  makeStyles,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@material-ui/core";
import { Alert } from "@material-ui/lab";
import { differenceInMinutes, format, parseISO } from "date-fns";
import ValidationTableRow from "./ValidationTableRow";
import { useHistory } from "react-router";
import { ArrowBackRounded } from "@material-ui/icons";

interface ValidationJobResultsLocationParams {
  jobId: string;
}

const useStyles = makeStyles({
  headerTitle: {
    fontWeight: "bold",
    fontSize: "1.1rem",
  },
  resultsTable: {
    maxHeight: "60vh",
  },
  infoBanner: {
    marginTop: "1em",
    marginBottom: "1em",
  },
});

const ValidationJobResults: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [totalProblemReports, setTotalProblemReports] = useState(0);
  const [problemReports, setProblemReports] = useState<ValidationProblem[]>([]);
  const [jobDetails, setJobDetails] = useState<ValidationJob | undefined>(
    undefined
  );
  const [lastError, setLastError] = useState<string | undefined>(undefined);

  const routerParams = useParams<ValidationJobResultsLocationParams>();
  const history = useHistory();

  const classes = useStyles();

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
        const response = await axios.get<ValidationProblemListResponse>(
          `/api/validation/${routerParams.jobId}/faults`
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

  useEffect(() => {
    refreshData();
  }, []);

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

  return (
    <>
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
                  <Typography>Problem detected at</Typography>
                </TableCell>
                <TableCell>
                  <Typography>Affected item</Typography>
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
      </Paper>
    </>
  );
};

export default ValidationJobResults;
