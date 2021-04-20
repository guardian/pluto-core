import React, { useEffect, useState } from "react";
import { RouteComponentProps, useHistory, useLocation } from "react-router-dom";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  Grid,
  makeStyles,
  Paper,
  TextField,
} from "@material-ui/core";
import { getProject, getProjectByVsid, updateProject } from "./helpers";
import SystemNotification, {
  SystemNotificationKind,
} from "../SystemNotification";

import ProjectEntryDeliverablesComponent from "./ProjectEntryDeliverablesComponent";
import { Breadcrumb } from "pluto-headers";
import ApplicableRulesSelector from "./ApplicableRulesSelector";
import moment from "moment";
import axios from "axios";
import ProductionOfficeSelector from "../common/ProductionOfficeSelector";
import StatusSelector from "../common/StatusSelector";
import { Helmet } from "react-helmet";
import ProjectEntryVaultComponent from "./ProjectEntryVaultComponent";

const useStyles = makeStyles({
  root: {
    display: "flex",
    flexDirection: "column",
    padding: "1rem",
    "& .MuiTextField-root": {
      width: "100%",
      marginBottom: "1rem",
    },
    "& .MuiFormControl-root": {
      width: "100%",
      marginBottom: "1rem",
    },
  },
  applicableRules: {
    display: "flex",
    flexDirection: "column",
  },
  formButtons: {
    display: "flex",
    marginTop: "2.5rem",
    justifyContent: "flex-end",
    "& Button": {
      marginLeft: "1rem",
    },
  },
});

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
  const classes = useStyles();
  const history = useHistory();

  const { state: projectFromList } = useLocation<Project | undefined>();
  const [project, setProject] = useState<Project>(
    projectFromList ?? EMPTY_PROJECT
  );
  const [projectType, setProjectType] = useState<ProjectType | undefined>(
    undefined
  );
  const [errorDialog, setErrorDialog] = useState<boolean>(false);

  const getProjectTypeData = async (projectTypeId: number) => {
    try {
      const response = await axios.get(`/api/projecttype/${projectTypeId}`);
      console.log("project type request got ", response.data);
      setProjectType(response.data.result as ProjectType);
    } catch (err) {
      console.error("Could not load project type information: ", err);
    }
  };
  // Fetch project from URL path
  useEffect(() => {
    // No need to fetch data if we navigated from the project list.
    // Only fetch data if loading this URL "directly".
    if (projectFromList) {
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

    return () => {
      isMounted = false;
    };
  }, []);

  const fieldChanged = (
    event: React.ChangeEvent<
      | HTMLTextAreaElement
      | HTMLInputElement
      | HTMLSelectElement
      | { name?: string; value: unknown }
    >,
    field: keyof Project
  ): void => {
    setProject({ ...project, [field]: event.target.value });
  };

  const checkboxChanged = (field: keyof Project, checked: boolean): void => {
    setProject({ ...project, [field]: !checked });
  };

  const subComponentErrored = (errorDesc: string) => {
    SystemNotification.open(SystemNotificationKind.Error, errorDesc);
  };

  const onProjectSubmit = async (
    event: React.FormEvent<HTMLFormElement>
  ): Promise<void> => {
    event.preventDefault();

    if (project.title) {
      try {
        await updateProject(project as Project);

        SystemNotification.open(
          SystemNotificationKind.Success,
          `Successfully updated Project "${project.title}"`
        );
        history.goBack();
      } catch {
        SystemNotification.open(
          SystemNotificationKind.Error,
          `Failed to update Project "${project.title}"`
        );
      }
    }
  };

  const closeDialog = () => {
    setErrorDialog(false);
    props.history.goBack();
  };

  return (
    <>
      {project ? (
        <Helmet>
          <title>[{project.title}] Details</title>
        </Helmet>
      ) : null}
      <div style={{ marginBottom: "0.8em" }}>
        <Breadcrumb
          projectId={project.id}
          plutoCoreBaseUri={`${deploymentRootPath.replace(/\/+$/, "")}`}
        />
      </div>
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
              <TextField
                label="Owner"
                value={project.user}
                onChange={(event) => fieldChanged(event, "user")}
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

              <ApplicableRulesSelector
                deletable={project.deletable}
                deep_archive={project.deep_archive}
                sensitive={project.sensitive}
                onChange={checkboxChanged}
              />
            </Grid>
          </Grid>
          <div className={classes.formButtons}>
            <Button
              className="cancel"
              variant="outlined"
              onClick={() => history.goBack()}
            >
              Back
            </Button>
            <Button type="submit" variant="outlined">
              Update
            </Button>
          </div>
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
