import React, { useEffect, useState } from "react";
import {
  Grid,
  IconButton,
  Input,
  makeStyles,
  TableCell,
  TableRow,
  TextField,
  Typography,
} from "@material-ui/core";
import { Close, DeleteForever, Edit, SaveAlt } from "@material-ui/icons";

interface VersionTranslationRowProps {
  entry: PremiereVersionTranslation;
  requestDelete: (id: number) => void;
  requestUpdate: (newValue: PremiereVersionTranslation) => Promise<void>;
}

const useStyles = makeStyles((theme) => ({
  editField: {
    width: "100%",
  },
}));

interface EditableFieldProps {
  value: string;
  onChange: (newValue: string) => void;
  canEdit: boolean;
  errorMessage?: string;
}

const EditableField: React.FC<EditableFieldProps> = (props) => {
  const classes = useStyles();

  return props.canEdit ? (
    <TextField
      className={classes.editField}
      value={props.value}
      onChange={(evt) => props.onChange(evt.target.value)}
      error={!!props.errorMessage}
      helperText={props.errorMessage}
    />
  ) : (
    <Typography>{props.value}</Typography>
  );
};

const VersionTranslationRow: React.FC<VersionTranslationRowProps> = (props) => {
  const [isEditing, setIsEditing] = useState(false);

  const [internalVersionString, setInternalVersionString] = useState("0");
  const [internalVersionValid, setInternalVersionValid] = useState(false);
  const [name, setName] = useState("");
  const [nameValid, setNameValid] = useState(false);
  const [displayedVersion, setDisplayedVersion] = useState("");
  const [displayedVersionValid, setDisplayedVersionValid] = useState(false);

  useEffect(() => {
    resetValues();
    if (props.entry.internalVersionNumber == 0) setIsEditing(true); //this is a blank row that has just been inserted
  }, [props.entry]);

  useEffect(() => {
    try {
      const intValue = parseInt(internalVersionString);
      if (intValue > 0) {
        setInternalVersionValid(true);
      } else {
        setInternalVersionValid(false);
      }
    } catch (err) {
      setInternalVersionValid(false);
    }
  }, [internalVersionString]);

  useEffect(() => {
    if (name == "") {
      setNameValid(false);
    } else {
      setNameValid(true);
    }
  }, [name]);

  const displayVersionValidator = /^\d+\.\d+\.\d+$/;

  useEffect(() => {
    setDisplayedVersionValid(displayVersionValidator.test(displayedVersion));
  }, [displayedVersion]);

  const resetValues = () => {
    setInternalVersionString(props.entry.internalVersionNumber.toString());
    setName(props.entry.name);
    setDisplayedVersion(props.entry.displayedVersion);
  };

  const cancelEditing = () => {
    setIsEditing(false);
    resetValues();
  };

  const saveEntry = () => {
    const internalVersion = parseInt(internalVersionString);
    if (internalVersion != 0 && name != "" && displayedVersion != "") {
      //on error. props.requestUpdate will reject and so then we don't clear the "editing" status
      props
        .requestUpdate({
          internalVersionNumber: internalVersion,
          name: name,
          displayedVersion: displayedVersion,
        })
        .then(() => setIsEditing(false));
    }
  };

  return (
    <TableRow>
      <TableCell>
        <EditableField
          value={internalVersionString}
          errorMessage={
            internalVersionValid
              ? undefined
              : "This must be a whole number greater than zero"
          }
          onChange={(newValue) => setInternalVersionString(newValue)}
          canEdit={isEditing}
        />
      </TableCell>
      <TableCell>
        <EditableField
          value={name}
          onChange={(newValue) => setName(newValue)}
          errorMessage={
            nameValid ? undefined : "You need to put in a descriptive name"
          }
          canEdit={isEditing}
        />
      </TableCell>
      <TableCell>
        <EditableField
          value={displayedVersion}
          onChange={(newValue) => setDisplayedVersion(newValue)}
          errorMessage={
            displayedVersionValid
              ? undefined
              : "You need to put in the Mac version number in the form n.n.n. If less than three numbers are shown pad it out with .0"
          }
          canEdit={isEditing}
        />
      </TableCell>
      <TableCell>
        <Grid container spacing={1}>
          <Grid item>
            {isEditing ? (
              <IconButton onClick={saveEntry}>
                <SaveAlt />
              </IconButton>
            ) : (
              <IconButton onClick={() => setIsEditing(true)}>
                <Edit />
              </IconButton>
            )}
          </Grid>
          <Grid item>
            {isEditing ? (
              <IconButton onClick={cancelEditing}>
                <Close />
              </IconButton>
            ) : (
              <IconButton
                onClick={() =>
                  props.requestDelete(props.entry.internalVersionNumber)
                }
              >
                <DeleteForever />
              </IconButton>
            )}
          </Grid>
        </Grid>
      </TableCell>
    </TableRow>
  );
};

export default VersionTranslationRow;
