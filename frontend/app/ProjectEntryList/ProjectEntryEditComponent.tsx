import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
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
import { validProductionOffices } from "../utils/constants";

const useStyles = makeStyles({
  root: {
    display: "flex",
    flexDirection: "column",
    padding: "1rem",
    "& form": {
      width: "400px",
      display: "flex",
      flexDirection: "column",
      alignItems: "flex-start",
      margin: 0,
    },
    "& .MuiTextField-root": {
      marginBottom: "1rem",
      width: "100%",
    },
  },
});

interface ProjectEntryEditComponentStateTypes {
  itemid?: string;
}

type ProjectEntryEditComponentProps = RouteComponentProps<
  ProjectEntryEditComponentStateTypes
>;

const ProjectEntryEditComponent: React.FC<ProjectEntryEditComponentProps> = (
  props
) => {
  const classes = useStyles();

  const [project, setProject] = useState<Project>({
    title: "",
    status: "",
    deletable: false,
    deep_archive: false,
    sensitive: false,
  } as Project);
  const [failed, setFailed] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;

    if (props.match.params.itemid !== "new") {
      const loadProject = async (): Promise<void> => {
        const id = Number(props.match.params.itemid);
        const project = await getProject(id);

        if (isMounted) {
          setProject(project);
          console.warn(project);
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
      HTMLTextAreaElement | HTMLInputElement | HTMLSelectElement
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

        setFailed(false);
      } catch {
        setFailed(true);
      }
    }
  };

  const closeSnackBar = (): void => {
    setFailed(false);
  };

  return (
    <>
      <Paper className={classes.root}>
        <>
          <h3>Edit project information</h3>
          <p className="information">
            The only part of the project information that it's possible to edit
            is the title.
          </p>
          <p className="information">
            Press "Confirm" to go ahead, or press Back to cancel.
          </p>
          {/*   id: number;
  projectTypeId: number;
  title: string;
  created: string;
  user: string;
  workingGroupId: number;
  commissionId: number;
  deletable: false;
  deep_archive: true;
  sensitive: false;
  status: string;
  productionOffice: string;*/}
          <form onSubmit={onProjectSubmit}>
            <TextField
              label="Project name"
              value={project.title}
              autoFocus
              onChange={(event) => fieldChanged(event, "title")}
            ></TextField>
            {/* <FormControl>
              <InputLabel id="demo-simple-select-label">Age</InputLabel>
              <Select
                labelId="demo-simple-select-label"
                id="demo-simple-select"
                value={project.user}
                onChange={(event: any) => fieldChanged(event, "user")}
              >
                <MenuItem value={10}>Ten</MenuItem>
                <MenuItem value={20}>Twenty</MenuItem>
                <MenuItem value={30}>Thirty</MenuItem>
              </Select>
            </FormControl> */}
            <TextField
              label="Status"
              value={project.status}
              autoFocus
              onChange={(event) => fieldChanged(event, "status")}
            ></TextField>

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
                  <MenuItem value={productionOffice}>
                    {productionOffice}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <h4>Applicable rules</h4>
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
            <Button type="submit" variant="outlined">
              Confirm
            </Button>
          </form>
        </>
      </Paper>
      <Snackbar
        open={failed}
        autoHideDuration={4000}
        onClose={closeSnackBar}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <SnackbarContent
          style={{
            backgroundColor: "#f44336",
          }}
          message={
            <span id="client-snackbar">{`Failed to update Project!`}</span>
          }
        />
      </Snackbar>
    </>
  );
};

export default ProjectEntryEditComponent;
