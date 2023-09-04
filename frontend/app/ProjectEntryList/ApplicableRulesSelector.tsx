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
}

const ApplicableRulesSelector: React.FC<ApplicableRulesSelectorProps> = (
  props
) => {
  const handleDeletableChange = () => {
    if (!props.deletable) {
      props.onChange("deep_archive", false);
    }
    props.onChange("deletable", !props.deletable);
  };
  const handleDeepArchiveChange = () => {
    if (!props.deep_archive) {
      props.onChange("deletable", false);
    }
    props.onChange("deep_archive", !props.deep_archive);
  };

  return (
    <>
      <Typography>Applicable rules</Typography>
      <Tooltip title="Selecting this option means that the data will not be backed up. Once the project is set to 'Completed', it will be permanently deleted.">
        <FormControlLabel
          control={
            <Checkbox
              checked={props.deletable}
              onChange={handleDeletableChange}
              name="deletable"
              color="primary"
            />
          }
          label="Deletable"
        />
      </Tooltip>
      <Tooltip title="This option will backup the data to long-term storage">
        <FormControlLabel
          control={
            <Checkbox
              checked={props.deep_archive}
              onChange={handleDeepArchiveChange}
              name="deep_archive"
              color="primary"
            />
          }
          label="Deep Archive"
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
            />
          }
          label="Sensitive"
        />
      </Tooltip>
    </>
  );
};

export default ApplicableRulesSelector;
