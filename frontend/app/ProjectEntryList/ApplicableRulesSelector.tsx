import React from "react";
import {
  Checkbox,
  FormControlLabel,
  Grid,
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
      props.onChange("deep_archive", true);
    }
    props.onChange("deletable", props.deletable);
  };
  const handleDeepArchiveChange = () => {
    if (!props.deep_archive) {
      props.onChange("deletable", true);
    }
    props.onChange("deep_archive", props.deep_archive);
  };

  return (
    <>
      <Typography>Applicable rules</Typography>
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
    </>
  );
};

export default ApplicableRulesSelector;
