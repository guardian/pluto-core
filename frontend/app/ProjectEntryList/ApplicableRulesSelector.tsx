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
  const { deletable, deep_archive, sensitive, onChange } = props;

  const handleDeletableChange = () => {
    onChange("deletable", deletable);
    if (!deletable) {
      onChange("deep_archive", false);
    }
  };

  const handleDeepArchiveChange = () => {
    onChange("deep_archive", deep_archive);
    if (!deep_archive) {
      onChange("deletable", false);
    }
  };

  const handleSensitiveChange = () => {
    onChange("sensitive", sensitive);
  };

  return (
    <>
      <Typography>Applicable rules</Typography>
      <Tooltip title="Selecting this option means that the data will not be backed up. Once the project is set to 'Completed', it will be permanently deleted.">
        <FormControlLabel
          control={
            <Checkbox
              checked={deletable}
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
              checked={deep_archive}
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
              checked={sensitive}
              onChange={handleSensitiveChange}
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
