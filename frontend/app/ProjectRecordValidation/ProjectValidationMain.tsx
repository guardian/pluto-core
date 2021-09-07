import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet";
import { LinearProgress, Typography } from "@material-ui/core";
import { getValidationRecords } from "./ProjectValidationDataService";
import { Alert } from "@material-ui/lab";
import ValidationJobsTable from "./ValidationJobsTable";

const ProjectValidationMain: React.FC = () => {
  const [totalJobsCount, setTotalJobsCount] = useState(0);
  const [loadedJobs, setLoadedJobs] = useState<ValidationJob[]>([]);
  const [loading, setLoading] = useState(false);
  const [lastError, setLastError] = useState<string | undefined>(undefined);

  const [selectedUserName, setSelectedUserName] = useState("");
  const [selectedStatus, setSelectedStatus] = useState("");

  useEffect(() => {
    updateData();
  }, []);

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
      setLastError(err);
      setLoading(false);
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
      <div>
        <Typography>
          There have been {totalJobsCount} recorded validation runs
        </Typography>
        <ValidationJobsTable data={loadedJobs} />
      </div>
    </>
  );
};

export default ProjectValidationMain;
