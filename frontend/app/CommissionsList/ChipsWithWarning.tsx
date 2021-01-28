import React, { useState, useEffect } from "react";
import ChipInput from "material-ui-chip-input";
import { Typography } from "@material-ui/core";
import { makeStyles } from "@material-ui/core/styles";

interface ChipsWithWarningProps {
  label?: string;
  onChange: (newValue: CommissionFullRecord) => void;
  commission: CommissionFullRecord;
  classes?: Record<string, string>;
}

const useStyles = makeStyles((theme) => ({
  warningText: {
    color: theme.palette.warning.dark,
  },
}));

const ChipsWithWarning: React.FC<ChipsWithWarningProps> = (props) => {
  const [dirty, setDirty] = useState(false);

  const classes = useStyles();

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
          You must press ENTER otherwise your value here won't be saved
        </Typography>
      ) : null}
    </>
  );
};

export default ChipsWithWarning;
