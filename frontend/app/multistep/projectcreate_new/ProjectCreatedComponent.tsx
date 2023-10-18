import React from "react";
import { CheckCircle } from "@material-ui/icons";
import { Box, Button, Grid, Typography } from "@material-ui/core";
import AssetFolderLink from "../../ProjectEntryList/AssetFolderLink";
import {
  openProject,
  updateProjectOpenedStatus,
} from "../../ProjectEntryList/helpers";
import { createProjectDeliverable } from "../../utils/api";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import { Helmet } from "react-helmet";
import { useHistory } from "react-router-dom";
import { useGuardianStyles } from "~/misc/utils";

interface ProjectCreatedComponentProps {
  projectId: number;
  commissionId: number;
  title: string;
}

const ProjectCreatedComponent: React.FC<ProjectCreatedComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();

  const history = useHistory();

  const createDeliverable = async (): Promise<void> => {
    try {
      await createProjectDeliverable(
        props.projectId,
        props.commissionId,
        props.title
      );
      window.location.assign(`/deliverables/project/${props.projectId}`); //can't just use js-history because we are jumping to another app
    } catch (error) {
      console.error(error);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        "Failed to create Project Deliverable"
      );
    }
  };
  const buttonStyle = {
    minWidth: "175px",
    minHeight: "50px",
  };

  return (
    <div className={classes.container}>
      <Helmet>
        <title>Edit project created - Pluto</title>
      </Helmet>

      <div
        style={{
          display: "flex",
          flexDirection: "row",
          justifyContent: "center",
          marginBottom: "50px",
        }}
      >
        <CheckCircle className={classes.success} style={{ width: "80px" }} />
        <Typography className={classes.bannerText} gutterBottom>
          Your project has been created! <br />
        </Typography>
      </div>
      <br />
      <Grid container spacing={3}>
        <Grid item xs={8}>
          <Typography>
            Open the Asset Folder in Finder so you can start importing media
          </Typography>
        </Grid>
        <Grid item xs={4}>
          <AssetFolderLink projectId={props.projectId} />
        </Grid>
        <Grid item xs={8}>
          <Typography>Start work on the project</Typography>
        </Grid>
        <Grid item xs={4}>
          <Button
            variant="contained"
            color="primary"
            onClick={async () => {
              try {
                await openProject(props.projectId);
              } catch (error) {
                SystemNotification.open(
                  SystemNotifcationKind.Error,
                  `An error occurred when attempting to open the project. `
                );
                console.error(error);
              }
              try {
                await updateProjectOpenedStatus(props.projectId);
              } catch (error) {
                console.error(error);
              }
            }}
            style={buttonStyle}
          >
            Open project
          </Button>
        </Grid>
        <Grid item xs={8}>
          <Typography>Add some deliverables right away</Typography>
        </Grid>
        <Grid item xs={4}>
          <Button
            variant="outlined"
            onClick={createDeliverable}
            style={buttonStyle}
          >
            Add Deliverables
          </Button>
        </Grid>
        <Grid item xs={8}>
          <Typography>Return to the Projects list</Typography>
        </Grid>
        <Grid item xs={4}>
          <Button
            onClick={() => history.push("/project?mine")}
            variant="outlined"
            style={buttonStyle}
          >
            Projects list
          </Button>
        </Grid>
      </Grid>
    </div>
  );
};

export default ProjectCreatedComponent;
