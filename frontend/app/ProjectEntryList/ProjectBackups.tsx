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
  ListItem,
  ListItemIcon,
  ListItemText,
  makeStyles,
  Paper,
  Tooltip,
  Typography,
} from "@material-ui/core";
import { Breadcrumb } from "pluto-headers";
import {
  ArrowBack,
  FileCopy,
  LocationOn,
  PermMedia,
  WarningRounded,
} from "@material-ui/icons";
import { getProject, getProjectFiles } from "./helpers";
import { Alert } from "@material-ui/lab";
import { format, parseISO } from "date-fns";
import clsx from "clsx";
import { DEFAULT_DATE_FORMAT } from "../../types/constants";
import BackupEntry from "./BackupEntry";

declare var deploymentRootPath: string;

const useStyles = makeStyles((theme) => ({
  warningIcon: {
    color: theme.palette.warning.main,
  },
  inlineIcon: {
    marginRight: "6px",
    verticalAlign: "top",
  },
  centeredDiv: {
    paddingTop: "2em",
    paddingBottom: "2em",
    justifyContent: "space-around",
  },
  emphasised: {
    fontWeight: theme.typography.fontWeightBold,
  },
}));

const PrimaryFilesIndicator: React.FC<{ primaryFiles: FileEntry[] }> = (
  props
) => {
  const [timeString, setTimeString] = useState("");

  useEffect(() => {
    if (props.primaryFiles.length >= 1) {
      try {
        const date = parseISO(props.primaryFiles[0].mtime);
        setTimeString(format(date, DEFAULT_DATE_FORMAT));
      } catch (err) {
        console.error(
          "Could not format date ",
          props.primaryFiles[0].mtime,
          ": ",
          err
        );
        setTimeString("(invalid date)");
      }
    }
  }, [props.primaryFiles]);

  if (props.primaryFiles.length == 0) {
    return (
      <Alert severity="error">There is no openable file on this project!</Alert>
    );
  } else if (props.primaryFiles.length == 1) {
    return (
      <Alert severity="info">
        The main file is {props.primaryFiles[0].filepath} which was created at{" "}
        {timeString}
      </Alert>
    );
  } else {
    return (
      <Alert severity="warning">
        There are {props.primaryFiles.length} potential primary files on this
        project
      </Alert>
    );
  }
};

const ProjectBackups: React.FC<RouteComponentProps<{ itemid: string }>> = (
  props
) => {
  const [project, setProject] = useState<Project | undefined>(undefined);
  const [dialogErrString, setDialogErrString] = useState<string | undefined>(
    undefined
  );

  const [primaryFiles, setPrimaryFiles] = useState<FileEntry[]>([]);
  const [backupFiles, setBackupFiles] = useState<FileEntry[]>([]);

  const history = useHistory();
  const classes = useStyles();

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

  const sortFileEntryFunc = (a: FileEntry, b: FileEntry) =>
    a.mtime.localeCompare(b.mtime);

  useEffect(() => {
    if (project) {
      getProjectFiles(project.id).then((fileList) => {
        setPrimaryFiles(fileList.filter((f) => !f.backupOf));
        setBackupFiles(
          fileList.filter((f) => !!f.backupOf).sort(sortFileEntryFunc)
        );
      });
    }
  }, [project]);

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
          <div>
            <PrimaryFilesIndicator primaryFiles={primaryFiles} />
          </div>
          <List>
            {backupFiles.map((f, idx) => (
              <BackupEntry
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
                project.status == "New" || project.status == "Killed" ? (
                  <Typography className={classes.emphasised}>
                    There are no backups of this project file because it is{" "}
                    {project.status}
                  </Typography>
                ) : (
                  <Typography className={classes.emphasised}>
                    <WarningRounded
                      className={clsx(classes.warningIcon, classes.inlineIcon)}
                    />
                    This project has not been backed up yet
                  </Typography>
                )
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

export default ProjectBackups;
