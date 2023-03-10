import React, { useEffect, useState } from "react";
import { Paper, Button, Typography } from "@material-ui/core";
import {
  createProjectDeliverable,
  getProjectDeliverableSummary,
} from "../utils/api";
import ErrorOutlineIcon from "@material-ui/icons/ErrorOutline";
import WarningIcon from "@material-ui/icons/Warning";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import { useGuardianStyles } from "~/misc/utils";

const tableHeaderTitles: string[] = ["Filename", "Size", "Status"];

interface ProjectEntryDeliverablesComponentProps {
  project: Project;
  onError?: (errorDesc: string) => void;
}

const ProjectEntryDeliverablesComponent: React.FC<ProjectEntryDeliverablesComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();
  const [deliverable, setDeliverables] = useState<Deliverable[]>([]);
  const [
    deliverableCount,
    setDeliverableCount,
  ] = useState<DeliverablesCount | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [failed, setFailed] = useState<string>("");
  const { project } = props;

  useEffect(() => {
    const loadProjectDeliverables = async () => {
      try {
        const deliverableCount = await getProjectDeliverableSummary(project.id);

        setDeliverableCount(deliverableCount);
      } catch (error) {
        if (error) {
          let message = "Failed to fetch Deliverables!";
          if (error?.response?.status === 503) {
            message = `${message} - Reason: Pluto-Deliverables application is offline for some reason`;
          }

          setFailed(message);
          console.error(message, error);
        }
      }

      setLoading(false);
    };

    setLoading(true);
    loadProjectDeliverables();
  }, []);

  useEffect(() => {
    if (failed && props.onError)
      props.onError("Could not load deliverables information");
  }, [failed]);

  const createDeliverable = async (): Promise<void> => {
    try {
      await createProjectDeliverable(
        project.id,
        project.commissionId,
        project.title
      );
      window.location.assign(`/deliverables/project/${project.id}`);
    } catch (error) {
      console.error(error);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        "Failed to create Project Deliverable"
      );
    }
  };

  if (loading) {
    return (
      <div className={classes.loading}>
        <Typography variant="h4">Loading...</Typography>
      </div>
    );
  }

  return (
    <Paper className={classes.projectDeliverable}>
      <Typography variant="h4">Project Deliverables</Typography>

      {failed && (
        <Typography variant="subtitle1" className="error">
          <div className="content">
            <ErrorOutlineIcon />
            <span className="message">{failed}</span>
          </div>
        </Typography>
      )}

      {deliverableCount ? (
        <>
          <Typography variant="subtitle1">
            There are currently {deliverableCount.total_asset_count}{" "}
            deliverables attached to this project.
          </Typography>
          {deliverableCount.unimported_asset_count > 0 ? (
            <Typography variant="subtitle1" style={{ color: "red" }}>
              <WarningIcon style={{ color: "red" }} />{" "}
              {deliverableCount.unimported_asset_count} are not correctly
              imported! This needs to be fixed.
            </Typography>
          ) : null}
          <div className="button-container">
            <Button
              variant="outlined"
              onClick={() => window.open(`/deliverables/project/${project.id}`)}
            >
              View Deliverables
            </Button>
          </div>
        </>
      ) : (
        <>
          <Typography variant="subtitle1">
            There is no deliverables bundle for this project. Click "Add
            Deliverables" to create one.
          </Typography>
          <div className="button-container">
            <Button variant="outlined" onClick={createDeliverable}>
              Add Deliverables
            </Button>
          </div>
        </>
      )}
    </Paper>
  );
};

export default ProjectEntryDeliverablesComponent;
