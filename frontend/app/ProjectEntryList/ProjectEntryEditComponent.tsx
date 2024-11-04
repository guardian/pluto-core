import React, { useEffect, useState } from "react";
import { RouteComponentProps, useHistory, useLocation } from "react-router-dom";

import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  FormControlLabel,
  Grid,
  IconButton,
  Paper,
  Checkbox,
  TextField,
  Tooltip,
  Typography,
  styled,
  Icon,
  DialogTitle,
  RadioGroup,
  FormControl,
  Radio,
} from "@material-ui/core";
import {
  getProject,
  getProjectByVsid,
  openProject,
  updateProject,
  updateProjectOpenedStatus,
  getSimpleProjectTypeData,
  getMissingFiles,
  downloadProjectFile,
} from "./helpers";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";

import ProjectEntryDeliverablesComponent from "./ProjectEntryDeliverablesComponent";
import { Breadcrumb } from "@guardian/pluto-headers";
import ApplicableRulesSelector from "./ApplicableRulesSelector";
import moment from "moment";
import axios from "axios";
import ProductionOfficeSelector from "../common/ProductionOfficeSelector";
import StatusSelector from "../common/StatusSelector";
import { Helmet } from "react-helmet";
import ProjectEntryVaultComponent from "./ProjectEntryVaultComponent";
import { FileCopy, PermMedia, CloudDownload } from "@material-ui/icons";
import UsersAutoComplete from "../common/UsersAutoComplete";
import { useGuardianStyles } from "~/misc/utils";
import ObituarySelector from "~/common/ObituarySelector";
import AssetFolderLink from "~/ProjectEntryList/AssetFolderLink";
import { isLoggedIn } from "~/utils/api";
import ProjectFileUpload from "./ProjectFileUpload";
import FolderIcon from "@material-ui/icons/Folder";
import BuildIcon from "@material-ui/icons/Build";
import LaunchIcon from "@material-ui/icons/Launch";
import RestoreIcon from "@material-ui/icons/Restore";

declare var deploymentRootPath: string;

interface ProjectEntryEditComponentStateTypes {
  itemid?: string;
}

type ProjectEntryEditComponentProps = RouteComponentProps<
  ProjectEntryEditComponentStateTypes
>;

const EMPTY_PROJECT: Project = {
  commissionId: -1,
  created: new Date().toLocaleDateString(),
  deep_archive: false,
  deletable: false,
  id: 0,
  productionOffice: "UK",
  isObitProject: null,
  projectTypeId: 0,
  sensitive: false,
  status: "New",
  title: "",
  user: "",
  workingGroupId: 0,
  confidential: false,
};

const FixPermissionsButton = styled(Button)({
  marginRight: "8px",
  minWidth: "170px",
  "&:hover": {
    backgroundColor: "#A9A9A9",
  },
});

const DownloadProjectButton = styled(Button)({
  marginLeft: "0px",
  marginRight: "8px",
  minWidth: "240px",
  "&:hover": {
    backgroundColor: "#A9A9A9",
  },
});

const RestoreButton = styled(Button)({
  marginLeft: "8px",
  marginRight: "0px",
  minWidth: "240px",
  "&:hover": {
    backgroundColor: "#A9A9A9",
  },
});

const ProjectEntryEditComponent: React.FC<ProjectEntryEditComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();
  const history = useHistory();

  const { state: projectFromList } = useLocation<Project | undefined>();
  const [project, setProject] = useState<Project>(
    projectFromList ?? EMPTY_PROJECT
  );
  const [projectType, setProjectType] = useState<ProjectType | undefined>(
    undefined
  );
  const [errorDialog, setErrorDialog] = useState<boolean>(false);
  const [projectTypeData, setProjectTypeData] = useState<any>({});
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [initialProject, setInitialProject] = useState<Project | null>(null);
  const [missingFiles, setMissingFiles] = useState<MissingFiles[]>([]);
  const [userAllowedBoolean, setUserAllowedBoolean] = useState<boolean>(true);
  const [openRestoreDialog, setOpenRestoreDialog] = useState(false);
  const [retrievalType, setRetrievalType] = useState<"Bulk" | "Standard">(
    "Bulk"
  );
  const [restoreStats, setRestoreStats] = useState<{
    numberOfFiles: number;
    totalSize: number;
    standardRetrievalCost: number;
    bulkRetrievalCost: number;
  } | null>(null);
  const [isLoadingStats, setIsLoadingStats] = useState(false);

  const getProjectTypeData = async (projectTypeId: number) => {
    try {
      const response = await axios.get(`/api/projecttype/${projectTypeId}`);
      console.log("project type request got ", response.data);
      setProjectType(response.data.result as ProjectType);
    } catch (err) {
      console.error("Could not load project type information: ", err);
    }
  };

  const API_PROJECT_RESTORE = "/project-restore";

  const restoreProject = async () => {
    try {
      const path = await getProjectPath(project.id);
      console.log("project.id", project.id);
      console.log("path", path);
      console.log("isLoggedIn.uid", (await isLoggedIn()).uid);
      console.log("project.name", project.title);
      console.log("retrievalType", retrievalType);
      const response = await axios.post(`${API_PROJECT_RESTORE}/restore`, {
        id: project.id,
        path: path,
        user: (await isLoggedIn()).uid,
        project: project.title,
        retrievalType: retrievalType,
      });

      if (response.status === 200 || 202) {
        console.log("Project restore initiated successfully:", response.data);
      } else {
        throw new Error(
          `Project restore failed with status: ${response.status}`
        );
      }

      await updateProject({ ...project, status: "In Production" });

      // Update local state
      setProject({ ...project, status: "In Production" });
      setInitialProject({ ...project, status: "In Production" });

      // Notify the user of success
      SystemNotification.open(
        SystemNotifcationKind.Success,
        `Successfully restored project "${project.title}" to "In Production" status.`
      );
    } catch (error) {
      console.error("Failed to restore project:", error);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Failed to restore the project. Please try again.`
      );
    }
  };

  const getProjectPath = async (projectId: number) => {
    try {
      const response = await axios.get(`/api/project/${projectId}/assetfolder`);
      const projectPath = response.data.result.value;
      console.log(
        "project path request got ",
        projectPath,
        "for project id",
        projectId
      );
      return projectPath;
    } catch (err) {
      console.error("Could not load project path information: ", err);
    }
  };

  const hasChanges = () => {
    // Perform deep equality check
    return JSON.stringify(initialProject) !== JSON.stringify(project);
  };

  const getMissingFilesData = async () => {
    try {
      const id = Number(props.match.params.itemid);
      const returnedRecords = await getMissingFiles(id);
      setMissingFiles(returnedRecords);
    } catch {
      console.log("Could not load missing files.");
    }
  };

  // Fetch project from URL path
  useEffect(() => {
    // No need to fetch data if we navigated from the project list.
    // Only fetch data if loading this URL "directly".
    if (projectFromList) {
      setProject(projectFromList);
      setInitialProject(projectFromList);
      return;
    }

    let isMounted = true;

    if (props.match.params.itemid !== "new") {
      const loadProject = async (): Promise<void> => {
        if (!props.match.params.itemid) throw "No project ID to load";
        const id = Number(props.match.params.itemid);

        try {
          const project = isNaN(id)
            ? await getProjectByVsid(props.match.params.itemid)
            : await getProject(id);
          if (isMounted) {
            setProject(project);
            setInitialProject(project);
          }
          await getProjectTypeData(project.projectTypeId);
        } catch (error) {
          if (error.message == "Request failed with status code 404") {
            setErrorDialog(true);
          }
        }
      };

      loadProject();
    }

    const fetchWhoIsLoggedIn = async () => {
      try {
        const loggedIn = await isLoggedIn();
        setIsAdmin(loggedIn.isAdmin);
      } catch {
        setIsAdmin(false);
      }
    };

    fetchWhoIsLoggedIn();

    getMissingFilesData();

    return () => {
      isMounted = false;
    };
  }, []);

  const fieldChangedValue = (value: unknown, field: keyof Project): void => {
    setProject({ ...project, [field]: value });
  };

  const fieldChangedValueArray = (
    value: string[],
    field: keyof Project
  ): void => {
    setProject({ ...project, [field]: value.join("|") });
  };

  const fieldChanged = (
    event: React.ChangeEvent<
      | HTMLTextAreaElement
      | HTMLInputElement
      | HTMLSelectElement
      | { name?: string; value: unknown }
    >,
    field: keyof Project
  ): void => {
    fieldChangedValue(event.target.value, field);
  };

  const checkboxChanged = (field: keyof Project, checked: boolean): void => {
    setProject({ ...project, [field]: !checked });
  };

  const updateDeletableAndDeepArchive = (
    newDeletable: boolean,
    newDeepArchive: boolean
  ) => {
    setProject((prevProject) => ({
      ...prevProject,
      deletable: newDeletable,
      deep_archive: newDeepArchive,
    }));
  };

  const subComponentErrored = (errorDesc: string) => {
    SystemNotification.open(SystemNotifcationKind.Error, errorDesc);
  };

  const onProjectSubmit = async (
    event: React.FormEvent<HTMLFormElement>
  ): Promise<void> => {
    event.preventDefault();

    if (project.isObitProject != null) {
      project.isObitProject = project.isObitProject.toLowerCase();
    }

    if (project.title) {
      if (project.status == "Completed") {
        if (missingFiles.length == 0) {
          try {
            await updateProject(project as Project);

            SystemNotification.open(
              SystemNotifcationKind.Success,
              `Successfully updated project "${project.title}"`
            );
            history.goBack();
            setInitialProject({ ...project });
          } catch {
            SystemNotification.open(
              SystemNotifcationKind.Error,
              `Failed to update project "${project.title}"`
            );
          }
        } else {
          SystemNotification.open(
            SystemNotifcationKind.Error,
            `Cannot change project "${project.title}" status to Completed because it has missing files.`
          );
        }
      } else {
        try {
          await updateProject(project as Project);

          SystemNotification.open(
            SystemNotifcationKind.Success,
            `Successfully updated project "${project.title}"`
          );
          history.goBack();
          setInitialProject({ ...project });
        } catch {
          SystemNotification.open(
            SystemNotifcationKind.Error,
            `Failed to update project "${project.title}"`
          );
        }
      }
    }
  };

  const closeDialog = () => {
    setErrorDialog(false);
    props.history.goBack();
  };

  const imagePath = (imageName: string) => {
    return "/pluto-core/assets/images/types/" + imageName + ".png";
  };

  useEffect(() => {
    const fetchProjectTypeData = async () => {
      try {
        const projectTypeData = await getSimpleProjectTypeData();
        setProjectTypeData(projectTypeData);
      } catch (error) {
        console.error("Could get project type data:", error);
      }
    };

    fetchProjectTypeData();
  }, []);

  const generateUserName = (inputString: string) => {
    if (inputString.includes("@")) {
      const splitString = inputString.split("@", 1)[0];
      const userNameConst = splitString.replace(".", "_");
      return userNameConst;
    }
    return inputString;
  };

  useEffect(() => {
    const userAllowed = async () => {
      try {
        const loggedIn = await isLoggedIn();
        if (loggedIn.isAdmin) {
          setUserAllowedBoolean(true);
        } else if (
          project.user
            .split("|")
            .includes(generateUserName(loggedIn.uid).toLowerCase())
        ) {
          setUserAllowedBoolean(true);
        } else {
          setUserAllowedBoolean(false);
        }
      } catch {
        console.error(
          "Error attempting to check if user is allowed access to this page."
        );
      }
    };

    if (project.confidential) {
      userAllowed();
    }
  }, [project.user]);

  const fixPermissions = async (project: number) => {
    try {
      const response = await axios.get(
        `/api/project/${project}/fixPermissions`
      );
      console.log("Attempt to fix permissions got ", response.data);
      SystemNotification.open(
        SystemNotifcationKind.Success,
        `Successfully started attempt at fixing permissions.`
      );
    } catch (err) {
      console.error("Could not fix permissions: ", err);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Failed to start attempt at fixing permissions.`
      );
    }
  };

  const removeWarning = async (project: number) => {
    try {
      const response = await axios.get(`/api/project/${project}/removeWarning`);
      console.log("Attempt to remove warning got ", response.data);
      SystemNotification.open(
        SystemNotifcationKind.Success,
        `Successfully started attempt at removing warning.`
      );
    } catch (err) {
      console.error("Could not remove warning: ", err);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Failed to start attempt at removing warning.`
      );
    }
  };

  const handleRestoreClick = async () => {
    const path = await getProjectPath(project.id);
    if (path) {
      console.log("Calling getRestoreStats with path: ", path);
      await getRestoreStats(path);
      setOpenRestoreDialog(true);
    }
  };

  const handleCloseRestoreDialog = () => {
    setOpenRestoreDialog(false);
  };

  const handleConfirmRestore = () => {
    handleCloseRestoreDialog();
    restoreProject();
  };

  const getRestoreStats = async (projectPath: string) => {
    console.log("Calling getRestoreStats with path: ", projectPath);
    try {
      setIsLoadingStats(true);
      const response = await axios.post(`${API_PROJECT_RESTORE}/stats`, {
        path: projectPath,
      });
      console.log("Raw response data:", response.data);
      setRestoreStats(response.data);
    } catch (error) {
      console.error("Failed to get restore stats:", error);
    } finally {
      setIsLoadingStats(false);
    }
  };

  return (
    <>
      {userAllowedBoolean ? (
        <>
          {project ? (
            <Helmet>
              {console.log("project type is: ", project.projectTypeId)}
              {console.log(
                "projectTypeData[project.projectTypeId]",
                projectTypeData[project.projectTypeId]
              )}
              <title>[{project.title}] Details</title>
            </Helmet>
          ) : null}
          <Grid container spacing={3}>
            <Grid item xs={9} md={6}>
              <Breadcrumb
                projectId={project.id}
                plutoCoreBaseUri={`${deploymentRootPath.replace(/\/+$/, "")}`}
              />
            </Grid>
            <Grid item xs={12} md={9}>
              <Box
                display="flex"
                flexDirection="column"
                alignItems="start"
                flexWrap="wrap"
              >
                <Box
                  display="flex"
                  flexDirection="row"
                  alignItems="center"
                  style={{ marginBottom: "10px" }}
                >
                  <Button
                    startIcon={<LaunchIcon />}
                    style={{ marginRight: "8px", minWidth: "160px" }}
                    className={classes.openProjectButton}
                    variant="contained"
                    color="primary"
                    onClick={async () => {
                      try {
                        await openProject(project.id);
                      } catch (error) {
                        SystemNotification.open(
                          SystemNotifcationKind.Error,
                          `An error occurred when attempting to open the project.`
                        );
                        console.error(error);
                      }

                      try {
                        await updateProjectOpenedStatus(project.id);
                      } catch (error) {
                        console.error(error);
                      }
                    }}
                  >
                    Open project
                  </Button>
                  <div style={{ marginRight: "-6px", minWidth: "160px" }}>
                    <AssetFolderLink
                      projectId={project.id}
                      onClick={(event) => {
                        event.stopPropagation();
                      }}
                    />
                  </div>
                  {projectTypeData[project.projectTypeId] == "Audition" ||
                  projectTypeData[project.projectTypeId] == "Cubase" ? (
                    <Tooltip
                      title="View Project File Backups"
                      style={{ marginRight: "0px", minWidth: "10px" }}
                    >
                      <IconButton
                        disableRipple
                        className={classes.noHoverEffect}
                        onClick={() =>
                          history.push(
                            `/project/${project.id}/assetfolderbackups`
                          )
                        }
                      >
                        <FileCopy />
                      </IconButton>
                    </Tooltip>
                  ) : (
                    <Tooltip
                      title="View Project File Backups"
                      style={{ marginLeft: "5px", minWidth: "10px" }}
                    >
                      <IconButton
                        disableRipple
                        className={classes.noHoverEffect}
                        onClick={() =>
                          history.push(`/project/${project.id}/backups`)
                        }
                      >
                        <FileCopy />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Tooltip title="View project's media">
                    <IconButton
                      disableRipple
                      className={classes.noHoverEffect}
                      onClick={() =>
                        window.location.assign(`/vs/project/${project.id}`)
                      }
                    >
                      <PermMedia />
                    </IconButton>
                  </Tooltip>
                  <Box flexGrow={1} />
                  {project.status == "Completed" && isAdmin && (
                    <Tooltip title="Restore project assets from deep archive">
                      <IconButton
                        style={{ padding: "4px" }}
                        disableRipple
                        className={classes.noHoverEffect}
                        onClick={handleRestoreClick}
                      >
                        <RestoreIcon />
                      </IconButton>
                    </Tooltip>
                  )}
                </Box>
                <Box
                  display="flex"
                  flexDirection="row"
                  alignItems="center"
                  style={{ marginBottom: "10px" }}
                >
                  <Tooltip title="Fix permissions issues in this projects' asset folder.">
                    <FixPermissionsButton
                      startIcon={<BuildIcon />}
                      onClick={async () => {
                        try {
                          await fixPermissions(project.id);
                        } catch (error) {
                          SystemNotification.open(
                            SystemNotifcationKind.Error,
                            `An error occurred when attempting to fix permissions.`
                          );
                          console.error(error);
                        }
                      }}
                      variant="contained"
                    >
                      Fix&nbsp;Permissions
                    </FixPermissionsButton>
                  </Tooltip>
                  {projectTypeData[project.projectTypeId] == "Premiere" ? (
                    <ProjectFileUpload
                      projectId={project.id}
                    ></ProjectFileUpload>
                  ) : null}
                  {projectTypeData[project.projectTypeId] ==
                  ("Premiere" || "After Effects" || "Prelude") ? (
                    <Tooltip title="Download the Premiere Pro file for this project, to work on a laptop or elsewhere">
                      <DownloadProjectButton
                        startIcon={<CloudDownload />}
                        variant="contained"
                        onClick={async () => {
                          try {
                            await downloadProjectFile(project.id);
                          } catch (error) {
                            SystemNotification.open(
                              SystemNotifcationKind.Error,
                              `An error occurred when attempting to download the project file.`
                            );
                            console.error(error);
                          }
                        }}
                      >
                        Download&nbsp;Project&nbsp;File
                      </DownloadProjectButton>
                    </Tooltip>
                  ) : null}
                </Box>
              </Box>
            </Grid>
          </Grid>

          <Paper className={classes.root} elevation={3}>
            <form onSubmit={onProjectSubmit}>
              <Grid container xs={12} direction="row" spacing={3}>
                <Grid item xs={6}>
                  <TextField
                    label="Project name"
                    value={project.title}
                    autoFocus
                    onChange={(event) => fieldChanged(event, "title")}
                  />
                  <Tooltip title="Add a name here to make this into an obituary.">
                    <span>
                      <ObituarySelector
                        label="Obituary"
                        value={project.isObitProject ?? ""}
                        valueDidChange={(evt, newValue) =>
                          fieldChangedValue(newValue, "isObitProject")
                        }
                      />
                    </span>
                  </Tooltip>
                  <UsersAutoComplete
                    label="Owner"
                    shouldValidate={true}
                    value={project.user}
                    valueDidChange={(evt, newValue) => {
                      if (newValue) {
                        fieldChangedValueArray(newValue, "user");
                      } else {
                        fieldChangedValue(newValue, "user");
                      }
                    }}
                  />
                  <StatusSelector
                    value={project.status}
                    onChange={(event) => fieldChanged(event, "status")}
                  />
                  <ProductionOfficeSelector
                    label="Production Office"
                    value={project.productionOffice}
                    onChange={(evt: any) =>
                      fieldChanged(evt, "productionOffice")
                    }
                  />
                  {isAdmin ? (
                    <Button
                      style={{ minWidth: "129px", marginTop: "16px" }}
                      href={"/pluto-core/project/" + project.id + "/deletedata"}
                      color="secondary"
                      variant="contained"
                    >
                      Delete&nbsp;Data
                    </Button>
                  ) : null}
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    disabled={true}
                    value={moment(project.created).format(
                      "MMM Do, YYYY, hh:mm a"
                    )}
                    label="Created"
                  />
                  {projectType ? (
                    <TextField
                      disabled={true}
                      value={`${projectType.name} v${projectType.targetVersion}`}
                      label="Project type"
                    />
                  ) : null}
                  <img
                    src={imagePath(projectTypeData[project.projectTypeId])}
                    style={{ marginBottom: "1em" }}
                  />
                  <ApplicableRulesSelector
                    deletable={project.deletable}
                    deep_archive={project.deep_archive}
                    sensitive={project.sensitive}
                    onChange={updateDeletableAndDeepArchive}
                    disabled={!isAdmin}
                  />
                  <br />
                  <br />
                  <Tooltip title="Select this option if this is a sensitive project and you do not want other users besides the project owners to be able to view or access this project via Pluto.">
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={project.confidential}
                          name="confidential"
                          color="primary"
                          onChange={() =>
                            checkboxChanged(
                              "confidential",
                              project.confidential
                            )
                          }
                        />
                      }
                      label="Private"
                    />
                  </Tooltip>
                  <div
                    style={{ height: "48px" }}
                    className={classes.formButtons}
                  >
                    {hasChanges() && ( // Only render if changes have been made
                      <Button
                        type="submit"
                        color="secondary"
                        variant="contained"
                      >
                        Save changes
                      </Button>
                    )}
                  </div>
                </Grid>
              </Grid>
            </form>
          </Paper>
          {project === EMPTY_PROJECT ? null : (
            <ProjectEntryDeliverablesComponent
              project={project}
              onError={subComponentErrored}
            />
          )}
          {missingFiles.length === 0 ? null : (
            <Paper
              className={classes.root}
              elevation={3}
              style={{ marginTop: "1rem" }}
            >
              <Grid container xs={12} direction="row" spacing={3}>
                <Grid item xs={8}>
                  <Typography variant="h4">Warning</Typography>
                </Grid>
                <Grid item xs={4}>
                  <Button
                    style={{
                      width: "180px",
                    }}
                    onClick={async () => {
                      try {
                        await removeWarning(project.id);
                        getMissingFilesData();
                      } catch (error) {
                        SystemNotification.open(
                          SystemNotifcationKind.Error,
                          `An error occurred when attempting to remove the warning.`
                        );
                        console.error(error);
                      }
                    }}
                    variant="contained"
                  >
                    Remove&nbsp;Warning
                  </Button>
                </Grid>
                <Grid item xs={12}>
                  <Typography>
                    Some media files used in this project have been imported
                    from Internet Downloads or other areas. Please move these
                    files to the project's asset folder, otherwise these files
                    will be lost.
                    <br />
                    <br />
                    {missingFiles.map((file, index) => (
                      <>
                        {file.filepath}
                        <br />
                      </>
                    ))}
                  </Typography>
                </Grid>
              </Grid>
            </Paper>
          )}
          <Dialog
            open={errorDialog}
            onClose={closeDialog}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
          >
            <DialogContent>
              <DialogContentText id="alert-dialog-description">
                The requested project does not exist.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <Button onClick={closeDialog}>Close</Button>
            </DialogActions>
          </Dialog>

          <Dialog
            open={openRestoreDialog}
            onClose={handleCloseRestoreDialog}
            aria-labelledby="restore-dialog-title"
            aria-describedby="restore-dialog-description"
          >
            <DialogTitle id="restore-dialog-title">
              Confirm Project Restore
            </DialogTitle>
            <DialogContent>
              <DialogContentText id="restore-dialog-description">
                <strong>
                  Are you sure you want to restore this project's assets from
                  deep archive?
                </strong>
                <br />
                {console.log("Current restoreStats in render:", restoreStats)}
                {isLoadingStats ? (
                  "Loading project statistics..."
                ) : restoreStats ? (
                  <>
                    <br />
                    This restore will retrieve:
                    <br />• {restoreStats.numberOfFiles.toLocaleString()} files
                    <br />• {restoreStats.totalSize.toFixed(4)} GB total
                    <br />
                  </>
                ) : (
                  "No stats available"
                )}
              </DialogContentText>
              <FormControl component="fieldset" style={{ marginTop: "1rem" }}>
                <RadioGroup
                  value={retrievalType}
                  onChange={(e) =>
                    setRetrievalType(e.target.value as "Bulk" | "Standard")
                  }
                >
                  {restoreStats &&
                    (() => {
                      return (
                        <>
                          <FormControlLabel
                            value="Bulk"
                            control={<Radio />}
                            label={`Bulk Restore (5-12 hours, Estimated cost: $${restoreStats.bulkRetrievalCost.toFixed(
                              4
                            )} USD)`}
                          />
                          <FormControlLabel
                            value="Standard"
                            control={<Radio />}
                            label={`Standard Restore(2-5 hours, Estimated cost: $${restoreStats.standardRetrievalCost.toFixed(
                              4
                            )} USD)`}
                          />
                        </>
                      );
                    })()}
                </RadioGroup>
              </FormControl>
            </DialogContent>
            <DialogActions>
              <Button onClick={handleCloseRestoreDialog} color="primary">
                Cancel
              </Button>
              <Button onClick={handleConfirmRestore} color="primary" autoFocus>
                Proceed
              </Button>
            </DialogActions>
          </Dialog>
        </>
      ) : (
        <div>You have no access to this project.</div>
      )}
    </>
  );
};
export default ProjectEntryEditComponent;
