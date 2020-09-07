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
  return (
    <>
      <Typography>Applicable rules</Typography>
      <FormControlLabel
        control={
          <Checkbox
            checked={props.deletable}
            onChange={() => props.onChange("deletable", props.deletable)}
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
            onChange={() => props.onChange("deep_archive", props.deep_archive)}
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
