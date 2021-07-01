import React from "react";
import { CheckCircle } from "@material-ui/icons";
import { makeStyles } from "@material-ui/core/styles";
import { Button, TableCell, Typography } from "@material-ui/core";
import AssetFolderLink from "../../ProjectEntryList/AssetFolderLink";
import { updateProjectOpenedStatus } from "../../ProjectEntryList/helpers";
import { createProjectDeliverable } from "../../utils/api";
import SystemNotification, {
  SystemNotificationKind,
} from "../../SystemNotification";
import { Helmet } from "react-helmet";
import { useHistory } from "react-router-dom";

interface ProjectCreatedComponentProps {
  projectId: number;
  commissionId: number;
  title: string;
}

const useStyles = makeStyles((theme) => ({
  success: {
    color: theme.palette.success.dark,
    width: "100px",
    height: "100px",
  },
  container: {
    padding: "1em",
  },
  bannerText: {
    fontWeight: theme.typography.fontWeightBold,
    textAlign: "center",
    marginBottom: "1em",
  },
}));

const ProjectCreatedComponent: React.FC<ProjectCreatedComponentProps> = (
  props
) => {
  const classes = useStyles();

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
        SystemNotificationKind.Error,
        "Failed to create Project Deliverable"
      );
    }
  };

  return (
    <div className={classes.container}>
      <Helmet>
        <title>Edit project created - Pluto</title>
      </Helmet>
      <div style={{ marginLeft: "auto", marginRight: "auto", width: "100px" }}>
        <CheckCircle className={classes.success} />
      </div>
      <Typography className={classes.bannerText}>
        Your project has been created.
        <br />
        Would you like to....
      </Typography>
      <table>
        <tbody>
          <tr>
            <td>
              <Typography>
                Open the Asset Folder in Finder so you can start importing media
              </Typography>
            </td>
            <td>
              <AssetFolderLink projectId={props.projectId} />
            </td>
          </tr>
          <tr>
            <td>
              <Typography>Start work on the project</Typography>
            </td>
            <td>
              <Button
                variant="contained"
                color="primary"
                onClick={async () => {
                  window.open(`pluto:openproject:${props.projectId}`, "_blank");
                  try {
                    await updateProjectOpenedStatus(props.projectId);
                  } catch (error) {
                    console.error(error);
                  }
                }}
              >
                Open project
              </Button>
            </td>
          </tr>
          <tr>
            <td>
              <Typography>Add some deliverables right away</Typography>
            </td>
            <td>
              <Button variant="outlined" onClick={createDeliverable}>
                Add Deliverables
              </Button>
            </td>
          </tr>
          <tr>
            <td>
              <Typography>Return to the Projects list</Typography>
            </td>
            <td>
              <Button
                onClick={() => history.push("/project?mine")}
                variant="outlined"
              >
                Projects list
              </Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
};

export default ProjectCreatedComponent;