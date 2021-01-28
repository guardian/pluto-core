import React, { useState, useEffect } from "react";

interface ChipsWithWarningProps {
  label?: string;
  onChange: (newValue: CommissionFullRecord) => void;
  commission: CommissionFullRecord;
  classes?: Record<string, string>;
}

const ChipsWithWarning: React.FC<ChipsWithWarningProps> = (props) => {
  const [dirty, setDirty] = useState(false);

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
        <Typography variant="caption">
          You must press ENTER to save a new value
        </Typography>
      ) : null}
    </>
  );
};

export default ChipsWithWarning;
