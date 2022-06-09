import React, { useState } from "react";
import ChipInput from "material-ui-chip-input";
import { Typography } from "@material-ui/core";
import { useGuardianStyles } from "~/misc/utils";

interface ChipsWithWarningProps {
  label?: string;
  onChange: (newValue: CommissionFullRecord) => void;
  commission: CommissionFullRecord;
  classes?: Record<string, string>;
  warningText?: string;
}

const ChipsWithWarning: React.FC<ChipsWithWarningProps> = (props) => {
  const [dirty, setDirty] = useState(false);

  const classes = useGuardianStyles();

  return (
    <>
      <ChipInput
        label="Owner"
        value={props.commission.owner == "" ? [] : [props.commission.owner]}
        onAdd={(newText: string) => {
          props.onChange({ ...props.commission, owner: newText });
          setDirty(false);
        }}
        onDelete={(deleted: string) => {
          props.onChange({ ...props.commission, owner: "" });
          setDirty(false);
        }}
        onUpdateInput={() => setDirty(true)}
      />
      {dirty ? (
        <Typography variant="caption" className={classes.warningText}>
          {props.warningText ??
            "You must press ENTER after typing a name here, otherwise your new value won't be saved"}
        </Typography>
      ) : null}
    </>
  );
};

export default ChipsWithWarning;
