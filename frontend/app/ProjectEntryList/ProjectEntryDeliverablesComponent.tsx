import React, { useEffect, useState } from "react";
import { Paper, Button, Typography, makeStyles } from "@material-ui/core";
import { getProjectDeliverables } from "../utils/api";
import ErrorOutlineIcon from "@material-ui/icons/ErrorOutline";

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
      marginTop: "6px",
    },
  },
});

interface ProjectEntryDeliverablesComponentProps {
  project: Project;
}

const ProjectEntryDeliverablesComponent: React.FC<ProjectEntryDeliverablesComponentProps> = (
  props
) => {
  const classes = useStyles();
  const [deliverablesCount, setDeliverablesCount] = useState<number>(0);
  const [failed, setFailed] = useState<string>("");
  const { project } = props;

  useEffect(() => {
    const loadProjectDeliverables = async () => {
      try {
        const deliverables = await getProjectDeliverables(project.id);

        setDeliverablesCount(deliverables?.length);
      } catch (error) {
        let message = "Failed to fetch Deliverables!";
        if (error?.response?.status === 503) {
          message = `${message} - Reason: Pluto-Deliverables application is offline for some reason`;
        }

        setFailed(message);
        console.error(message, error);
      }
    };

    loadProjectDeliverables();
  }, []);

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
      <Typography variant="subtitle1">
        Total number of Deliverables: {deliverablesCount}
      </Typography>

      <div className="button-container">
        <Button variant="outlined" href="/deliverables/">
          Go to Deliverables
        </Button>
      </div>
    </Paper>
  );
};

export default ProjectEntryDeliverablesComponent;
