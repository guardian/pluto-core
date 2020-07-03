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
} from "@material-ui/core";
import {
  createWorkingGroup,
  getWorkingGroup,
  updateWorkingGroup,
} from "./helpers";

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

interface WorkingGroupStateTypes {
  itemid?: string;
}

type WorkingGroupProps = RouteComponentProps<WorkingGroupStateTypes>;

const WorkingGroup: React.FC<WorkingGroupProps> = (props) => {
  const classes = useStyles();

  const [id, setId] = useState<number>(0);
  const [hide, setHide] = useState<boolean>(false);
  const [name, setName] = useState<string>("");
  const [commissioner, setCommissioner] = useState<string>("");
  const [failed, setFailed] = useState<boolean>(false);
  const [editing, setEditing] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;

    if (props.match.params.itemid !== "new") {
      const setWorkingGroup = async (
        itemid: string | undefined
      ): Promise<void> => {
        setEditing(true);

        const id = Number(itemid);
        const workingGroup = await getWorkingGroup(id);

        if (isMounted) {
          setId(workingGroup.id);
          setName(workingGroup.name);
          setCommissioner(workingGroup.commissioner);
          setHide(workingGroup.hide);
        }
      };
      setWorkingGroup(props.match.params.itemid);
    }

    return () => {
      isMounted = false;
    };
  }, []);

  const onNameChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    setName(event.target.value);
  };

  const onCommissionerChanged = (
    event: React.ChangeEvent<HTMLInputElement>
  ): void => {
    setCommissioner(event.target.value);
  };

  const onWorkingGroupSubmit = async (
    event: React.FormEvent<HTMLFormElement>
  ): Promise<void> => {
    event.preventDefault();

    if (name && commissioner) {
      try {
        if (editing) {
          await updateWorkingGroup({
            id,
            name,
            commissioner,
            hide,
          });
        } else {
          await createWorkingGroup({
            name,
            commissioner,
          });
        }

        setFailed(false);

        props.history.push({
          pathname: "/working-group",
          state: {
            success: true,
            editing,
            workingGroup: {
              id,
              name,
              commissioner,
              hide,
            },
          },
        });
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
          <Typography variant="h4" gutterBottom>
            {editing ? "Edit Working Group" : "Create a Working Group"}
          </Typography>
          <form onSubmit={onWorkingGroupSubmit}>
            <TextField
              label="Name"
              value={name}
              autoFocus
              onChange={onNameChanged}
            ></TextField>
            <TextField
              label="Commissioner"
              value={commissioner}
              onChange={onCommissionerChanged}
            ></TextField>
            <Button type="submit" variant="outlined">
              {editing ? "Save" : "Create"}
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
            <span id="client-snackbar">{`Failed to ${
              editing ? "update" : "create"
            } Working Group!`}</span>
          }
        />
      </Snackbar>
    </>
  );
};

export default WorkingGroup;
