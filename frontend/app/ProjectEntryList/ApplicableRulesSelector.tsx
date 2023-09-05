import React from "react";
import {
  Checkbox,
  FormControlLabel,
  Grid,
  Tooltip,
  Typography,
} from "@material-ui/core";

interface ApplicableRulesSelectorProps {
  deletable: boolean;
  deep_archive: boolean;
  sensitive: boolean;
  onChange: (field: keyof Project, prevValue: boolean) => void;
  disabled: boolean;
}

const ApplicableRulesSelector: React.FC<ApplicableRulesSelectorProps> = (
  props
) => {
  return (
    <>
      <Typography>Media management settings</Typography>
      <Tooltip title="This option will backup the data to long-term storage">
        <FormControlLabel
          control={
            <Checkbox
              checked={props.deep_archive}
              onChange={() =>
                props.onChange("deep_archive", props.deep_archive)
              }
              name="deep_archive"
              color="primary"
              disabled={props.disabled}
            />
          }
          label="Deep Archive"
        />
      </Tooltip>
      <Tooltip title="Selecting this option means that the data will not be backed up. Once the project is set to 'Completed', it will be permanently deleted.">
        <FormControlLabel
          control={
            <Checkbox
              checked={props.deletable}
              onChange={() => props.onChange("deletable", props.deletable)}
              name="deletable"
              color="primary"
              disabled={props.disabled}
            />
          }
          label="Deletable"
        />
      </Tooltip>
      <Tooltip title="Data marked as 'Sensitive' will not be stored in the cloud but will instead be stored on-premises.">
        <FormControlLabel
          control={
            <Checkbox
              checked={props.sensitive}
              onChange={() => props.onChange("sensitive", props.sensitive)}
              name="sensitive"
              color="primary"
              disabled={props.disabled}
            />
          }
          label="Sensitive"
        />
      </Tooltip>
    </>
  );
};

export default ApplicableRulesSelector;
