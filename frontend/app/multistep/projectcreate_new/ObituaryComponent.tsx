import React from "react";
import { Checkbox, FormControlLabel, Typography } from "@material-ui/core";
import ObituarySelector from "../../common/ObituarySelector";
import { useGuardianStyles } from "~/misc/utils";

interface Props {
  valueDidChange: (newValue: string) => void;
  checkBoxDidChange: (newValue: boolean) => void;
  value: string;
  isObituary: boolean;
}

const ObituaryComponent = ({
  valueDidChange,
  value,
  isObituary,
  checkBoxDidChange,
}: Props) => {
  const classes = useGuardianStyles();

  return (
    <div>
      <Typography variant="h3">Obituary</Typography>
      <FormControlLabel
        control={
          <Checkbox
            checked={isObituary}
            onChange={(evt) => checkBoxDidChange(evt.target.checked)}
          />
        }
        label="Is this an obituary?"
      />
      {isObituary ? (
        <ObituarySelector
          label="Obituary"
          value={value}
          valueDidChange={(e, val) => valueDidChange(val ?? "")}
          shouldValidate
        />
      ) : (
        <Typography className={classes.secondaryText} style={{ width: 400 }}>
          Select the check box to select the person for whom the obituary is
          intended.
        </Typography>
      )}
    </div>
  );
};

export default ObituaryComponent;

// <Autocomplete
//   id="obituary-combo-box"
//   value={value}
//   inputValue={inputValue}
//   onInputChange={(event, newInputValue) => {
//     setInputValue(newInputValue);
//   }}
//   options={names}
//   freeSolo
//   style={{ width: 400 }}
//   renderInput={(params) => (
//     <TextField
//       {...params}
//       label="Who is this obituary for?"
//       variant="standard"
//     />
//   )}
// />
