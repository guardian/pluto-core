import React from "react";
import {
  Checkbox,
  FormControlLabel,
  Typography,
  useTheme,
} from "@material-ui/core";
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
  const theme = useTheme();
  return (
    <div>
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
        <>
          <Typography
            style={{ fontSize: "0.75rem", color: theme.palette.text.secondary }}
            paragraph={false}
          >
            Start typing a person's name below
          </Typography>
          <ObituarySelector
            label="Obituary"
            value={value}
            valueDidChange={(e, val) => valueDidChange(val ?? "")}
            shouldValidate
          />
        </>
      ) : (
        <Typography
          style={{ fontSize: "0.75rem", color: theme.palette.text.secondary }}
        >
          Select to choose person for whom the obituary is intended.
        </Typography>
      )}
    </div>
  );
};

export default ObituaryComponent;
