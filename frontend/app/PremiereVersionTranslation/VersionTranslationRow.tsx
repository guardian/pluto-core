import React, { useEffect, useState } from "react";
import {
  Grid,
  IconButton,
  Input,
  makeStyles,
  TableCell,
  TableRow,
  Typography,
} from "@material-ui/core";
import { Close, DeleteForever, Edit, SaveAlt } from "@material-ui/icons";

interface VersionTranslationRowProps {
  entry: PremiereVersionTranslation;
  requestDelete: (id: number) => void;
  requestUpdate: (newValue: PremiereVersionTranslation) => void;
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
}

const EditableField: React.FC<EditableFieldProps> = (props) => {
  const classes = useStyles();

  return props.canEdit ? (
    <Input
      className={classes.editField}
      value={props.value}
      onChange={(evt) => props.onChange(evt.target.value)}
    />
  ) : (
    <Typography>{props.value}</Typography>
  );
};

const VersionTranslationRow: React.FC<VersionTranslationRowProps> = (props) => {
  const [isEditing, setIsEditing] = useState(false);

  const [internalVersion, setInternalVersion] = useState(0);
  const [name, setName] = useState("");
  const [displayedVersion, setDisplayedVersion] = useState("");

  useEffect(() => {
    resetValues();
    if (props.entry.internalVersionNumber == 0) setIsEditing(true); //this is a blank row that has just been inserted
  }, [props.entry]);

  const resetValues = () => {
    setInternalVersion(props.entry.internalVersionNumber);
    setName(props.entry.name);
    setDisplayedVersion(props.entry.displayedVersion);
  };

  const cancelEditing = () => {
    setIsEditing(false);
    resetValues();
  };

  const saveEntry = () => {
    if (internalVersion != 0 && name != "" && displayedVersion != "") {
      props.requestUpdate({
        internalVersionNumber: internalVersion,
        name: name,
        displayedVersion: displayedVersion,
      });
      setIsEditing(false);
    }
  };

  return (
    <TableRow>
      <TableCell>
        <EditableField
          value={internalVersion.toString()}
          onChange={(newValue) => setInternalVersion(parseInt(newValue))}
          canEdit={isEditing}
        />
      </TableCell>
      <TableCell>
        <EditableField
          value={name}
          onChange={(newValue) => setName(newValue)}
          canEdit={isEditing}
        />
      </TableCell>
      <TableCell>
        <EditableField
          value={displayedVersion}
          onChange={(newValue) => setDisplayedVersion(newValue)}
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
