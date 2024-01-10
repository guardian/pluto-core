import React, { useEffect, useState } from "react";
import {
  Button,
  Dialog,
  DialogTitle,
  DialogActions,
  DialogContent,
  DialogContentText,
  FormControlLabel,
  Radio,
  RadioGroup,
  Tooltip,
  Typography,
} from "@material-ui/core";
import { makeStyles } from "@material-ui/core/styles";

interface ApplicableRulesSelectorProps {
  deletable: boolean;
  deep_archive: boolean;
  sensitive: boolean;
  onChange: (newDeletable: boolean, newDeepArchive: boolean) => void;
  disabled: boolean;
}

interface PendingChange {
  field: keyof Project;
  prevValue: boolean;
}

const useStyles = makeStyles((theme) => ({
  disabledText: {
    color: "grey",
  },
  // Add styling for disabled radio button
  disabledRadio: {
    color: "grey",
  },
}));

const ApplicableRulesSelector: React.FC<ApplicableRulesSelectorProps> = (
  props
) => {
  const classes = useStyles();
  const [openDialog, setOpenDialog] = useState(false);
  const [pendingChange, setPendingChange] = useState<PendingChange | null>(
    null
  );

  const handleChange = (
    event: React.ChangeEvent<HTMLInputElement>,
    value: string
  ) => {
    if (value in props) {
      const key = value as keyof ApplicableRulesSelectorProps;
      console.log("Key: ", key);
      if (key === "deletable") {
        setPendingChange({ field: key, prevValue: props[key] });
        setOpenDialog(true);
      } else if (key === "deep_archive") {
        setPendingChange({ field: key, prevValue: props[key] });
        setSelectedOption(value);
        props.onChange(false, true);
      }
    }
  };

  const handleDialogClose = (confirm: boolean) => {
    setOpenDialog(false);
    if (confirm && pendingChange) {
      props.onChange(true, false);
    }
    setPendingChange(null);
  };

  const [selectedOption, setSelectedOption] = useState("");

  useEffect(() => {
    let option = "";
    if (props.deep_archive) {
      option = "deep_archive";
    } else if (props.deletable) {
      option = "deletable";
    }
    setSelectedOption(option);
  }, [props.deletable, props.deep_archive]);

  return (
    <>
      <Typography className={props.disabled ? classes.disabledText : ""}>
        Media management settings
      </Typography>
      <RadioGroup value={selectedOption} onChange={handleChange}>
        <Tooltip title="Data will be backed up to long-term storage">
          <FormControlLabel
            value="deep_archive"
            control={<Radio color="primary" disabled={props.disabled} />}
            label="Deep Archive"
          />
        </Tooltip>
        <Tooltip title="Data will not be backed up. Once the project is set to 'Completed', it will be permanently deleted.">
          <FormControlLabel
            value="deletable"
            control={<Radio color="primary" disabled={props.disabled} />}
            label="Deletable"
          />
        </Tooltip>
      </RadioGroup>
      <Tooltip title="Data marked as 'Sensitive' will not be stored in the cloud but will instead be stored on-premises.">
        <FormControlLabel
          control={
            <Radio
              checked={props.sensitive}
              className={props.disabled ? classes.disabledRadio : ""}
              name="sensitive"
              color="primary"
              disabled
            />
          }
          label="Sensitive"
        />
      </Tooltip>

      {/* Confirmation Dialog */}
      <Dialog open={openDialog} onClose={() => handleDialogClose(false)}>
        <DialogTitle>{"Are you sure?"}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Changing to "Deletable" means the project media will be permanently
            deleted after it is set to Completed and its completion date has
            passed.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => handleDialogClose(false)} color="primary">
            Cancel
          </Button>
          <Button
            onClick={() => handleDialogClose(true)}
            color="primary"
            autoFocus
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ApplicableRulesSelector;
