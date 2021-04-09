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

interface ProjectEntryVaultComponentProps {
  project: Project;
}

const ProjectEntryVaultComponent: React.FC<ProjectEntryVaultComponentProps> = (
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

  if (loading) {
    return (
      <div className={classes.loading}>
        <Typography variant="h4">Loading...</Typography>
      </div>
    );
  }

  return (
    <Paper className={classes.projectDeliverable}>
      <Typography variant="h4">Vaultdoor</Typography>
    </Paper>
  );
};

export default ProjectEntryVaultComponent;
