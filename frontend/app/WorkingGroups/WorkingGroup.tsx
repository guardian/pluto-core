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
import { createWorkingGroup, getWorkingGroup } from "./helpers";

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

  const [name, setName] = useState<string>("");
  const [commissioner, setCommissioner] = useState<string>("");
  const [failed, setFailed] = useState<boolean>(false);
  const [view, setView] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;

    if (props.match.params.itemid !== "new") {
      setView(true);
      const setWorkingGroup = async (
        itemid: string | undefined
      ): Promise<void> => {
        const id = Number(itemid);
        const workingGroup = await getWorkingGroup(id);
        if (isMounted) {
          setName(workingGroup.name);
          setCommissioner(workingGroup.commissioner);
        }
      };
      setWorkingGroup(props.match.params.itemid);
    }

    // returned function will be called on component unmount
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

  const addWorkingGroup = async (
    event: React.FormEvent<HTMLFormElement>
  ): Promise<void> => {
    event.preventDefault();

    if (name && commissioner) {
      try {
        await createWorkingGroup({
          name,
          commissioner,
        });
        setFailed(false);

        props.history.push({
          pathname: "/working-group",
          state: { success: true, name },
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
        {view ? (
          <>
            <Typography variant="h4" gutterBottom>
              Working Group
            </Typography>
            <p>Name: {name}</p>
            <p>Commissioner: {commissioner}</p>
          </>
        ) : (
          <>
            <Typography variant="h4" gutterBottom>
              Create a Working Group
            </Typography>
            <form onSubmit={addWorkingGroup}>
              <TextField
                label="Name"
                value={name}
                onChange={onNameChanged}
              ></TextField>
              <TextField
                label="Commissioner"
                value={commissioner}
                onChange={onCommissionerChanged}
              ></TextField>
              <Button type="submit" variant="outlined">
                Create
              </Button>
            </form>
          </>
        )}
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
            <span id="client-snackbar">Failed to create Working Group!</span>
          }
        />
      </Snackbar>
    </>
  );
};

export default WorkingGroup;
