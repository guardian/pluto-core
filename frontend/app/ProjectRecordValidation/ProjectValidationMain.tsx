import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet";
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Grid,
  LinearProgress,
  Typography,
} from "@material-ui/core";
import { getValidationRecords } from "./ProjectValidationDataService";
import { Alert } from "@material-ui/lab";
import ValidationJobsTable from "./ValidationJobsTable";
import axios from "axios";

const ProjectValidationMain: React.FC = () => {
  const [totalJobsCount, setTotalJobsCount] = useState(0);
  const [loadedJobs, setLoadedJobs] = useState<ValidationJob[]>([]);
  const [loading, setLoading] = useState(false);
  const [lastError, setLastError] = useState<string | undefined>(undefined);

  const [showingNewRun, setShowingNewRun] = useState(false);

  const [selectedUserName, setSelectedUserName] = useState("");
  const [selectedStatus, setSelectedStatus] = useState("");

  useEffect(() => {
    updateData();
  }, []);

  const userFilterClicked = (userName: string) => {
    setSelectedUserName((prevValue) =>
      prevValue === userName ? "" : userName
    );
    window.setTimeout(() => updateData(), 100);
  };

  const statusFilterClicked = (statusValue: string) => {
    setSelectedStatus((prevValue) =>
      prevValue === statusValue ? "" : statusValue
    );
    window.setTimeout(() => updateData(), 100);
  };

  const updateData = async () => {
    setLoading(true);
    try {
      const response = await getValidationRecords(
        selectedUserName === "" ? undefined : selectedUserName,
        selectedStatus === "" ? undefined : selectedStatus
      );

      setTotalJobsCount(response.totalCount);
      setLoadedJobs(response.jobs);
      setLoading(false);
    } catch (err) {
      setLastError(err.toString());
      setLoading(false);
    }
  };

  const triggerNewRun = async () => {
    try {
      setShowingNewRun(false);
      setLoading(true);
      const validationDoc = {
        validationType: "CheckAllFiles",
      };
      const response = await axios.post("/api/validation", validationDoc);
      window.setTimeout(() => updateData(), 1000); //reload the list
    } catch (err) {
      setLoading(false);
      setLastError(err.toString());
    }
  };

  return (
    <>
      <Helmet>
        <title>Check project data - Pluto</title>
      </Helmet>
      {loading ? <LinearProgress style={{ width: "100%" }} /> : undefined}
      <Typography variant="h2">Validate project files</Typography>

      {lastError ? <Alert severity="error">{lastError}</Alert> : undefined}

      <Grid
        container
        justify="space-between"
        style={{ marginTop: "1em", marginBottom: "1em" }}
      >
        <Grid item>
          <Typography>
            There have been {totalJobsCount} recorded validation runs
          </Typography>
        </Grid>
        <Grid item>
          <Button variant="contained" onClick={() => setShowingNewRun(true)}>
            Start New Run
          </Button>
        </Grid>
      </Grid>
      <ValidationJobsTable
        data={loadedJobs}
        onUserFilterClicked={userFilterClicked}
        onStatusFilterClicked={statusFilterClicked}
        currentUserFilter={selectedUserName}
        currentStatusFilter={selectedStatus}
      />

      <Dialog
        open={showingNewRun}
        style={{ minWidth: "640px", minHeight: "480px" }}
      >
        <DialogTitle>New run</DialogTitle>
        <DialogContent>There are no options yet</DialogContent>
        <Grid container justify="space-between" style={{ padding: "2em" }}>
          <Grid item>
            <Button onClick={() => setShowingNewRun(false)}>Close</Button>
          </Grid>
          <Grid item>
            <Button variant="contained" onClick={triggerNewRun}>
              Start run
            </Button>
          </Grid>
        </Grid>
      </Dialog>
    </>
  );
};

export default ProjectValidationMain;
