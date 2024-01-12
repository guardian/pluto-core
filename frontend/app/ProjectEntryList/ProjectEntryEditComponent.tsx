import React, { useEffect, useState } from "react";
import { RouteComponentProps, useHistory, useLocation } from "react-router-dom";
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  Grid,
  IconButton,
  Paper,
  TextField,
  Tooltip,
  makeStyles,
} from "@material-ui/core";
import {
  getProject,
  getProjectByVsid,
  openProject,
  updateProject,
  updateProjectOpenedStatus,
  getSimpleProjectTypeData,
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
import { FileCopy, PermMedia, CloudUpload } from "@material-ui/icons";
import UsersAutoComplete from "../common/UsersAutoComplete";
import { useGuardianStyles } from "~/misc/utils";
import ObituarySelector from "~/common/ObituarySelector";
import AssetFolderLink from "~/ProjectEntryList/AssetFolderLink";
import { isLoggedIn } from "~/utils/api";
import ProjectFileUpload from "./ProjectFileUpload";
import FolderIcon from "@material-ui/icons/Folder";

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
      try {
        await updateProject(project as Project);

        SystemNotification.open(
          SystemNotifcationKind.Success,
          `Successfully updated Project "${project.title}"`
        );
        history.goBack();
        setInitialProject({ ...project });
      } catch {
        SystemNotification.open(
          SystemNotifcationKind.Error,
          `Failed to update Project "${project.title}"`
        );
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

  return (
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
          <Box display="flex" flexDirection="row" alignItems="center">
            <Button
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
            <Tooltip title="Press this button to fix any permissions issues in this projects' asset folder.">
              <Button
                startIcon={<FolderIcon />}
                style={{
                  marginLeft: "14px",
                  marginRight: "8px",
                  minWidth: "170px",
                }}
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
              </Button>
            </Tooltip>

            {isAdmin ? (
              <Button
                style={{ marginRight: "8px", minWidth: "129px" }}
                href={"/pluto-core/project/" + project.id + "/deletedata"}
                color="secondary"
                variant="contained"
              >
                Delete&nbsp;Data
              </Button>
            ) : null}
            {projectTypeData[project.projectTypeId] == "Premiere" ? (
              <ProjectFileUpload projectId={project.id}></ProjectFileUpload>
            ) : null}
            <Tooltip
              title="View Project File Backups"
              style={{ marginRight: "0px", minWidth: "10px" }}
            >
              <IconButton
                disableRipple
                className={classes.noHoverEffect}
                onClick={() => history.push(`/project/${project.id}/backups`)}
              >
                <FileCopy />
              </IconButton>
            </Tooltip>

            <Tooltip title="See project's media">
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
                onChange={(evt: any) => fieldChanged(evt, "productionOffice")}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                disabled={true}
                value={moment(project.created).format("MMM Do, YYYY, hh:mm a")}
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

              <div style={{ height: "48px" }} className={classes.formButtons}>
                {hasChanges() && ( // Only render if changes have been made
                  <Button type="submit" color="secondary" variant="contained">
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
      {project === EMPTY_PROJECT ? null : (
        <ProjectEntryVaultComponent
          project={project}
          onError={subComponentErrored}
        />
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
  );
};
export default ProjectEntryEditComponent;
