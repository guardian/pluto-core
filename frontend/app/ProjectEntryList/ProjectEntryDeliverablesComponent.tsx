import React, { useEffect, useState } from "react";
import {
  Paper,
  Button,
  Typography,
  makeStyles,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableBody,
  TableCell,
} from "@material-ui/core";
import {
  getProjectDeliverables,
  createProjectDeliverable,
  getProjectDeliverableSummary,
} from "../utils/api";
import ErrorOutlineIcon from "@material-ui/icons/ErrorOutline";
import WarningIcon from "@material-ui/icons/Warning";
import SystemNotification, {
  SystemNotificationKind,
} from "../SystemNotification";

const useStyles = makeStyles({
  projectDeliverable: {
    display: "flex",
    flexDirection: "column",
    padding: "1rem",
    marginTop: "1rem",

    "& .MuiTypography-subtitle1": {
      marginTop: "6px",
      marginBottom: "6px",
    },
    "& .error": {
      backgroundColor: "rgb(211 47 47)",
      padding: "10px",
      color: "#FFF",
      "& .content": {
        display: "flex",
        alignItems: "center",

        "& .message": {
          marginLeft: "6px",
        },
      },
    },
    "& .button-container": {
      marginTop: "1rem",
    },
  },
  loading: {
    display: "flex",
    flexDirection: "column",
    width: "100%",
    alignItems: "center",
  },
});

const tableHeaderTitles: string[] = ["Filename", "Size", "Status"];

interface ProjectEntryDeliverablesComponentProps {
  project: Project;
}

const ProjectEntryDeliverablesComponent: React.FC<ProjectEntryDeliverablesComponentProps> = (
  props
) => {
  const classes = useStyles();
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

  const createDeliverable = async (): Promise<void> => {
    try {
      await createProjectDeliverable(project);
      window.location.assign(`/deliverables/project/${project.id}`);
    } catch (error) {
      console.error(error);
      SystemNotification.open(
        SystemNotificationKind.Error,
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
