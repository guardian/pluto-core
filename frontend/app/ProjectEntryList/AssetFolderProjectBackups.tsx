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
} from "@material-ui/core";
import { Breadcrumb } from "@guardian/pluto-headers";
import { ArrowBack, PermMedia, WarningRounded } from "@material-ui/icons";
import { getProject, getAssetFolderProjectFiles } from "./helpers";
import clsx from "clsx";
import AssetFolderBackupEntry from "./AssetFolderBackupEntry";
import { useGuardianStyles } from "~/misc/utils";
import { isLoggedIn } from "~/utils/api";

declare var deploymentRootPath: string;

const AssetFolderProjectBackups: React.FC<RouteComponentProps<{
  itemid: string;
}>> = (props) => {
  const [project, setProject] = useState<Project | undefined>(undefined);
  const [dialogErrString, setDialogErrString] = useState<string | undefined>(
    undefined
  );
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
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

  const fetchWhoIsLoggedIn = async () => {
    try {
      const loggedIn = await isLoggedIn();
      setIsAdmin(loggedIn.isAdmin);
    } catch {
      setIsAdmin(false);
    }
  };

  useEffect(() => {
    fetchWhoIsLoggedIn();
  }, []);

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
                isAdmin={isAdmin}
                projectId={project?.id}
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
