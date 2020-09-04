import React, { useEffect, useState } from "react";
import { RouteComponentProps, useLocation, useHistory } from "react-router-dom";
import {
  TextField,
  Paper,
  Button,
  Typography,
  Snackbar,
  SnackbarContent,
  makeStyles,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Checkbox,
  FormControlLabel,
} from "@material-ui/core";
import { getProject, updateProject } from "./helpers";
import {
  validProductionOffices,
  validProjectStatuses,
} from "../utils/constants";
import SystemNotification, {
  SystemNotificationKind,
} from "../SystemNotification";
import ProjectEntryDeliverablesComponent from "./ProjectEntryDeliverablesComponent";

const useStyles = makeStyles({
  root: {
    display: "flex",
    flexDirection: "column",
    padding: "1rem",
    "& form": {
      display: "grid",
      gridTemplateColumns: "repeat(2, 1fr)",
      width: "100%",
      gridGap: "0 50px",
      margin: "0.625rem 0 0 0",
    },
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
    marginTop: "0.625rem",
    "& .cancel": {
      marginLeft: "1rem",
    },
  },
});

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
        const id = Number(props.match.params.itemid);
        const project = await getProject(id);

        if (isMounted) {
          setProject({ ...project, id });
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

  return (
    <>
      <Paper className={classes.root}>
        <Typography variant="h2">Edit project information</Typography>
        <Typography variant="subtitle1">
          The only part of the project information that it's possible to edit is
          the title.
        </Typography>

        <Typography variant="subtitle1">
          Press Confirm to go ahead, or press Back to cancel.
        </Typography>
        <form onSubmit={onProjectSubmit}>
          <div>
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
            <FormControl>
              <InputLabel id="label-status">Status</InputLabel>
              <Select
                labelId="label-status"
                value={project.status}
                onChange={(event) => fieldChanged(event, "status")}
              >
                {validProjectStatuses.map((status) => (
                  <MenuItem key={status} value={status}>
                    {status}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl>
              <InputLabel id="demo-simple-select-label">
                Production Office
              </InputLabel>
              <Select
                labelId="demo-simple-select-label"
                id="demo-simple-select"
                value={project.productionOffice}
                onChange={(event: any) =>
                  fieldChanged(event, "productionOffice")
                }
              >
                {validProductionOffices.map((productionOffice) => (
                  <MenuItem value={productionOffice} key={productionOffice}>
                    {productionOffice}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </div>
          <div className={classes.applicableRules}>
            <Typography variant="h4">Applicable rules</Typography>
            <FormControlLabel
              control={
                <Checkbox
                  checked={project.deletable}
                  onChange={() =>
                    checkboxChanged("deletable", project.deletable)
                  }
                  name="deletable"
                  color="primary"
                />
              }
              label="Deletable"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={project.deep_archive}
                  onChange={() =>
                    checkboxChanged("deep_archive", project.deep_archive)
                  }
                  name="deep_archive"
                  color="primary"
                />
              }
              label="Deep Archive"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={project.sensitive}
                  onChange={() =>
                    checkboxChanged("sensitive", project.sensitive)
                  }
                  name="sensitive"
                  color="primary"
                />
              }
              label="Sensitive"
            />
          </div>
          <div className={classes.formButtons}>
            <Button type="submit" variant="outlined">
              Confirm
            </Button>
            <Button
              className="cancel"
              variant="outlined"
              onClick={() => history.goBack()}
            >
              Back
            </Button>
          </div>
        </form>
      </Paper>
      {project === EMPTY_PROJECT ? null : (
        <ProjectEntryDeliverablesComponent project={project} />
      )}
    </>
  );
};

export default ProjectEntryEditComponent;
