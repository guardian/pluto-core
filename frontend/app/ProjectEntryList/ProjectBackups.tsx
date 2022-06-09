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
import { Breadcrumb } from "pluto-headers";
import { ArrowBack, PermMedia, WarningRounded } from "@material-ui/icons";
import { getFileStorageMetadata, getProject, getProjectFiles } from "./helpers";
import { Alert } from "@material-ui/lab";
import { format, parseISO } from "date-fns";
import clsx from "clsx";
import { DEFAULT_DATE_FORMAT } from "../../types/constants";
import BackupEntry from "./BackupEntry";
import SizeFormatter from "../common/SizeFormatter";
import PremiereVersionTranslationView from "../EntryViews/PremiereVersionTranslationView";
import { useGuardianStyles } from "~/misc/utils";

declare var deploymentRootPath: string;

const PrimaryFilesIndicator: React.FC<{
  primaryFiles: FileEntry[];
  meta: Map<string, string>;
}> = (props) => {
  const [timeString, setTimeString] = useState("");

  const classes = useGuardianStyles();

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
        <ul style={{ listStyle: "none" }}>
          <li className={classes.noSpacing}>
            The main file is {props.primaryFiles[0].filepath} which was created
            at {timeString}
          </li>
          <li className={classes.noSpacing}>
            {props.primaryFiles[0].premiereVersion ? (
              <span>
                This is a Premiere project;{" "}
                <PremiereVersionTranslationView
                  internalVersion={props.primaryFiles[0].premiereVersion}
                />
              </span>
            ) : (
              <span>
                This is no Premiere version information on this project, maybe
                it's not Premiere
              </span>
            )}
          </li>
          <li>
            <>
              {props.meta.get("size") ? (
                <span>
                  The file size is{" "}
                  {<SizeFormatter bytes={props.meta.get("size")} />}
                </span>
              ) : undefined}
              {props.meta.get("lastModified") ? (
                <span>
                  {" "}
                  and it was last modified at {props.meta.get("lastModified")}
                </span>
              ) : undefined}
            </>
          </li>
        </ul>
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
  const [primaryFileMetadata, setPrimaryFileMetadata] = useState<
    Map<string, string>
  >(new Map());

  const history = useHistory();
  const classes = useGuardianStyles();

  useEffect(() => {
    if (primaryFiles.length > 0) {
      getFileStorageMetadata(primaryFiles[0].id)
        .then((info) => setPrimaryFileMetadata(info))
        .catch((err) => {
          console.error(
            "Can't get metadata for file id ",
            primaryFiles[0].id,
            ": ",
            err
          );
        });
    }
  }, [primaryFiles]);

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
            <PrimaryFilesIndicator
              primaryFiles={primaryFiles}
              meta={primaryFileMetadata}
            />
          </div>
          <List>
            {backupFiles.map((f, idx) => (
              <BackupEntry
                key={idx}
                fileId={f.id}
                filepath={f.filepath}
                version={f.version}
                premiereVersion={f.premiereVersion}
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
