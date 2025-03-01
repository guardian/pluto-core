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
  Select,
  MenuItem,
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
import { sortListByOrder } from "../utils/lists";

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

const EMPTY_FILE: FileEntry = {
  id: 0,
  filepath: "",
  storage: 0,
  user: "",
  version: 0,
  ctime: "",
  mtime: "",
  atime: "",
  hasContent: false,
  hasLink: false,
  premiereVersion: 0,
};

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
  const [knownVersions, setKnownVersions] = useState<
    PremiereVersionTranslation[]
  >([]);
  const [fileData, setFileData] = useState<FileEntry>(EMPTY_FILE);
  const [premiereProVersion, setPremiereProVersion] = useState<number>(1);

  const getProjectTypeData = async (projectTypeId: number) => {
    try {
      const response = await axios.get(`/api/projecttype/${projectTypeId}`);
      console.log("project type request got ", response.data);
      setProjectType(response.data.result as ProjectType);
    } catch (err) {
      console.error("Could not load project type information: ", err);
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
          await getFileForId(project.id);
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

    getPremiereVersionData();

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

  const isProjectOlderThanOneDay = () => {
    const createdUNIX = new Date(project.created).getTime();
    const currentUNIX = Date.now();
    return currentUNIX - createdUNIX >= 86400000;
  };

  const newStatusIsCompleted = () => project.status === "Completed";

  const abortChanges = () => {
    if (initialProject) {
      setProject(initialProject);
    }
  };

  const getPremiereVersionData = async () => {
    try {
      const response = await axios.get<
        ObjectListResponse<PremiereVersionTranslation>
      >("/api/premiereVersion");
      setKnownVersions(response.data.result);
    } catch (err) {
      console.error("Could not load Premiere version data: ", err);
    }
  };

  const getFileForId = async (projectId: number) => {
    try {
      const response = await axios.get(`/api/project/${projectId}/files`);
      setFileData(response.data.files.pop() as FileEntry);
    } catch (err) {
      console.error("Could not load project file information: ", err);
    }
  };

  useEffect(() => {
    if (fileData?.premiereVersion) {
      setPremiereProVersion(fileData.premiereVersion);
    }
  }, [fileData?.premiereVersion]);

  const handleVersionChange = (
    event: React.ChangeEvent<
      HTMLSelectElement | { version?: number; value: unknown }
    >
  ): void => {
    if (typeof event.target.value === "number") {
      setPremiereProVersion(event.target.value);
    }
  };

  const updateFileRecord = async (fileDataInput: FileEntry): Promise<void> => {
    try {
      const { status } = await axios.put<PlutoApiResponse<void>>(
        `/api/file/${fileDataInput.id}`,
        fileData
      );

      if (status !== 200) {
        throw new Error(
          `Could not update file ${fileDataInput.id}: server said ${status}`
        );
      }
    } catch (error) {
      console.error(error);
      throw error;
    }
  };

  const onVersionSubmit = async (
    event: React.FormEvent<HTMLFormElement>
  ): Promise<void> => {
    event.preventDefault();

    try {
      let fileDataToSend = fileData;

      fileDataToSend.premiereVersion = premiereProVersion;

      await updateFileRecord(fileDataToSend as FileEntry);

      SystemNotification.open(
        SystemNotifcationKind.Success,
        `Successfully updated Premiere Pro version to ${premiereProVersion}`
      );
    } catch {
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Failed to update Premiere Pro version to ${premiereProVersion}`
      );
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
                  {hasChanges() ? ( // Only render if changes have been made
                    isProjectOlderThanOneDay() ? (
                      <div
                        style={{ height: "48px" }}
                        className={classes.formButtons}
                      >
                        <Button
                          type="submit"
                          color="secondary"
                          variant="contained"
                        >
                          Save changes
                        </Button>
                      </div>
                    ) : newStatusIsCompleted() ? (
                      <div>
                        <Typography style={{ marginTop: "30px" }}>
                          Are you sure you want to set this project to Completed
                          so soon? If you have just copied a large number of
                          files to the project asset folder then please wait
                          another day before setting this to Completed, to
                          ensure all the files for this project have been
                          properly backed up.
                        </Typography>
                        <div
                          style={{ height: "48px" }}
                          className={classes.formButtons}
                        >
                          <Button
                            color="secondary"
                            variant="contained"
                            onClick={() => abortChanges()}
                          >
                            Come back later
                          </Button>
                          <Button
                            type="submit"
                            color="secondary"
                            variant="contained"
                          >
                            Save changes
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <div
                        style={{ height: "48px" }}
                        className={classes.formButtons}
                      >
                        <Button
                          type="submit"
                          color="secondary"
                          variant="contained"
                        >
                          Save changes
                        </Button>
                      </div>
                    )
                  ) : null}
                </Grid>
              </Grid>
            </form>
            {isAdmin && projectTypeData[project.projectTypeId] == "Premiere" ? (
              <form onSubmit={onVersionSubmit}>
                <Grid
                  container
                  xs={12}
                  direction="row"
                  style={{ width: "380" }}
                >
                  <Grid item xs={5} style={{ marginTop: "5" }}>
                    <Typography>Premiere Pro Version</Typography>
                  </Grid>
                  <Grid item xs={3}>
                    <Select
                      id="version"
                      value={premiereProVersion}
                      label="Premiere Pro Version"
                      onChange={handleVersionChange}
                      className={classes.versionSelect}
                    >
                      {sortListByOrder(
                        knownVersions,
                        "internalVersionNumber",
                        "desc"
                      ).map((entry, idx) => (
                        <MenuItem value={entry.internalVersionNumber}>
                          {entry.internalVersionNumber}
                        </MenuItem>
                      ))}
                    </Select>
                  </Grid>
                  <Grid item xs={4}>
                    <Button type="submit" color="secondary" variant="contained">
                      Set Version
                    </Button>
                  </Grid>
                </Grid>
              </form>
            ) : null}
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
        </>
      ) : (
        <div>You have no access to this project.</div>
      )}
    </>
  );
};
export default ProjectEntryEditComponent;
