import React, { useEffect, useState } from "react";
import { RouteComponentProps, useHistory } from "react-router";
import { Helmet } from "react-helmet";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContentText,
  DialogTitle,
  Grid,
  IconButton,
  List,
  Paper,
  Tooltip,
  Typography,
  DialogContent,
} from "@material-ui/core";
import { Breadcrumb } from "@guardian/pluto-headers";
import { ArrowBack, PermMedia, WarningRounded } from "@material-ui/icons";
import { getProject, getAssetFolderProjectFiles } from "./helpers";
import clsx from "clsx";
import AssetFolderBackupEntry from "./AssetFolderBackupEntry";
import { useGuardianStyles } from "~/misc/utils";
import axios from "axios";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";

declare var deploymentRootPath: string;

const AssetFolderProjectBackups: React.FC<RouteComponentProps<{
  itemid: string;
}>> = (props) => {
  const [project, setProject] = useState<Project | undefined>(undefined);
  const [dialogErrString, setDialogErrString] = useState<string | undefined>(
    undefined
  );
  const [openDialog, setOpenDialog] = useState(false);
  const [backupFiles, setBackupFiles] = useState<AssetFolderFileEntry[]>([]);
  const history = useHistory();
  const classes = useGuardianStyles();

  useEffect(() => {
    const loadProject = async () => {
      const p = await getProject(Number(props.match.params.itemid));
      setProject(p);
    };
    loadProject().catch((err) => {
      console.error(err);
      setDialogErrString(
        `Could not load backups for project ${props.match.params.itemid}, it probably does not exist. See the javascript console for more details.`
      );
    });
  }, [props.match.params.itemid]);

  const sortFileEntryFunc = (
    a: AssetFolderFileEntry,
    b: AssetFolderFileEntry
  ) => a.mtime.localeCompare(b.mtime);

  useEffect(() => {
    if (project) {
      getAssetFolderProjectFiles(project.id).then((fileList) => {
        setBackupFiles(fileList.sort(sortFileEntryFunc));
      });
    }
  }, [project]);

  const handleClickOpenDialog = () => {
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
  };

  const handleConfirmUpload = () => {
    handleCloseDialog();
    handleRestore();
  };

  const handleRestore = async () => {
    try {
      const request =
          "/api/project/" + props.match.params.itemid + "/restoreForAssetFolder";
      const response = await axios.put(request, null, {
        headers: {
          "Content-Type": "application/json",
        },
      });
      console.log(response.data);
      SystemNotification.open(
          SystemNotifcationKind.Success,
          `${response.data.detail}`
      );
    } catch (error) {
      console.error("Error restoring file:", error);
      SystemNotification.open(
          SystemNotifcationKind.Error,
          `Failed to restore project: ${error}`
      );
    }
  };

  return (
    <>
      {project ? (
        <Helmet>
          <title>[{project.title}] Backups</title>
        </Helmet>
      ) : null}
      <Grid
        container
        justifyContent="space-between"
        style={{ marginBottom: "0.8em" }}
      >
        <Grid item>
          {project ? (
            <Breadcrumb
              projectId={project?.id}
              plutoCoreBaseUri={`${deploymentRootPath.replace(/\/+$/, "")}`}
            />
          ) : undefined}
        </Grid>
        <Grid item>
          <Button
              color="secondary"
              variant="contained"
              onClick={handleClickOpenDialog}
          >
            Restore
          </Button>
          {/* Confirmation Dialog */}
          <Dialog
              open={openDialog}
              onClose={handleCloseDialog}
              aria-labelledby="update-file-dialog-title"
              aria-describedby="update-file-dialog-description"
          >
            <DialogTitle id="update-file-dialog-title">
              Confirm Restoration of Backed up Project Files:
            </DialogTitle>
            <DialogContent>
              <DialogContentText id="update-file-dialog-description">
                You are about to restore all the backed up project files shown on this page. This will result in a folder being created in the project's asset folder named "RestoredProjectFiles", and within that a list of all Cubase/Audition files we have backed up. Once you have identified which project file you need, please move it out of this folder and into the root of project’s asset folder, before you carry on working with it.
                <br />
                <br />
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <Button onClick={handleCloseDialog} color="primary">
                Cancel
              </Button>
              <Button onClick={handleConfirmUpload} color="primary" autoFocus>
                Proceed
              </Button>
            </DialogActions>
          </Dialog>
        </Grid>
        <Grid item>
          <Grid container spacing={2}>
            <Grid item>
              <Tooltip title="Back to project page">
                <IconButton
                  onClick={() => history.push(`/project/${project?.id}`)}
                >
                  <ArrowBack />
                </IconButton>
              </Tooltip>
            </Grid>
            <Grid item>
              <Tooltip title="See project's media">
                <IconButton
                  onClick={() =>
                    window.location.assign(`/vs/project/${project?.id}`)
                  }
                >
                  <PermMedia />
                </IconButton>
              </Tooltip>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
      {project ? (
        <Paper elevation={3}>
          <List>
            {backupFiles.map((f, idx) => (
              <AssetFolderBackupEntry
                key={idx}
                fileId={f.id}
                filepath={f.filepath}
                version={f.version}
              />
            ))}
          </List>
          <Grid
            container
            className={classes.centeredDiv}
            justifyContent="space-around"
          >
            <Grid item>
              {backupFiles.length == 0 ? (
                <Typography className={classes.emphasised}>
                  <WarningRounded
                    className={clsx(classes.warningIcon, classes.inlineIcon)}
                  />
                  This project has not been backed up yet
                </Typography>
              ) : undefined}
            </Grid>
          </Grid>
        </Paper>
      ) : undefined}
      {dialogErrString ? (
        <Dialog open={!!dialogErrString}>
          <DialogTitle>Could not load backups information</DialogTitle>
          <DialogContentText>{dialogErrString}</DialogContentText>
          <DialogActions>
            <Button variant="contained" onClick={() => history.goBack()}>
              Go back
            </Button>
          </DialogActions>
        </Dialog>
      ) : undefined}
    </>
  );
};

export default AssetFolderProjectBackups;
