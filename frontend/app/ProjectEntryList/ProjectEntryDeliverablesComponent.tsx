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
import { getProjectDeliverables, createProjectDeliverable } from "../utils/api";
import ErrorOutlineIcon from "@material-ui/icons/ErrorOutline";
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
  const [deliverables, setDeliverables] = useState<Deliverable[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [failed, setFailed] = useState<string>("");
  const { project } = props;

  useEffect(() => {
    const loadProjectDeliverables = async () => {
      try {
        const deliverables = await getProjectDeliverables(project.id);

        setDeliverables(deliverables);
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
      window.location.assign("/deliverables/");
    } catch (error) {
      if (error) {
        console.error(error);
        SystemNotification.open(
          SystemNotificationKind.Error,
          "Failed to create Project Deliverable"
        );
      }
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

      {(!deliverables || deliverables.length === 0) && (
        <>
          <Typography variant="subtitle1">
            No deliverables exists. Click "Create Deliverable" to create a
            deliverable
          </Typography>
          <div className="button-container">
            <Button variant="outlined" onClick={createDeliverable}>
              Create Deliverable
            </Button>
          </div>
        </>
      )}

      {deliverables?.length > 0 && (
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                {tableHeaderTitles.map((title, index) => (
                  <TableCell key={index}>{title}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {deliverables.map((deliverable, index) => (
                <TableRow key={index}>
                  <TableCell>{deliverable.filename || ""}</TableCell>
                  <TableCell>{deliverable.size_string || ""}</TableCell>
                  <TableCell>{deliverable.status_string || ""}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  );
};

export default ProjectEntryDeliverablesComponent;
