import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
import {
  TextField,
  Paper,
  Button,
  Typography,
  makeStyles,
  Checkbox,
  FormControlLabel,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
} from "@material-ui/core";
import {
  createWorkingGroup,
  getWorkingGroup,
  updateWorkingGroup,
} from "./helpers";
import { SystemNotification, SystemNotifcationKind } from "pluto-headers";

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
  hide_control: {
    marginBottom: "20px",
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
  const [editing, setEditing] = useState<boolean>(false);
  const [errorDialog, setErrorDialog] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;

    if (props.match.params.itemid !== "new") {
      const setWorkingGroup = async (
        itemid: string | undefined
      ): Promise<void> => {
        setEditing(true);

        const id = Number(itemid);
        try {
          const workingGroup = await getWorkingGroup(id);
          if (isMounted) {
            setId(workingGroup.id);
            setName(workingGroup.name);
            setCommissioner(workingGroup.commissioner);
            setHide(workingGroup.hide);
          }
        } catch (error) {
          if (error.message == "Request failed with status code 404") {
            setErrorDialog(true);
          }
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

  const onHideChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    setHide(event.target.checked);
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

        SystemNotification.open(
          SystemNotifcationKind.Success,
          `Successfully ${
            editing ? "updated" : "created"
          } Working Group: "${name}"`
        );

        props.history.push("/working-group");
      } catch {
        SystemNotification.open(
          SystemNotifcationKind.Error,
          `Failed to ${editing ? "update" : "create"} Working Group!`
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
            />
            <TextField
              label="Commissioner"
              value={commissioner}
              onChange={onCommissionerChanged}
            />
            <FormControlLabel
              className={classes.hide_control}
              control={<Checkbox checked={hide} onChange={onHideChanged} />}
              label="Discontinued"
            />
            <Button type="submit" variant="outlined">
              {editing ? "Save" : "Create"}
            </Button>
          </form>
        </>
      </Paper>
      <Dialog
        open={errorDialog}
        onClose={closeDialog}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            The requested working group does not exist.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default WorkingGroup;
