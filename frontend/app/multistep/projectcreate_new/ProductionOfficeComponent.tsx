import React from "react";
import { MenuItem, Select, Typography } from "@material-ui/core";
import { projectCreateStyles } from "./CommonStyles";

interface ProductionOfficeComponentProps {
  valueWasSet: (newValue: ProductionOffice) => void;
  value: string;
  extraText?: string;
}

const ProductionOfficeComponent: React.FC<ProductionOfficeComponentProps> = (
  props
) => {
  const classes = projectCreateStyles();

  //we could load these in dynamically from the backend, but as they hardly ever change it seems better
  //to go with the more efficient approach of defining them here
  const validProductionOffices = ["UK", "US", "Aus"];
  const productionOfficeNames = [
    "London (UK)",
    "New York or San Francisco (US)",
    "Sydney (Aus)",
  ];

  return (
    <div>
      <Typography variant="h3">Where are you working from</Typography>
      <Typography>
        We need to know which production office you are working out of,
        <br />
        i.e. where the commissioner who green-lit this project usually works.
        {props.extraText ? props.extraText : ""}
      </Typography>
      <div className={classes.floatCentre}>
        <Select
          value={props.value}
          onChange={(evt) =>
            props.valueWasSet(evt.target.value as ProductionOffice)
          }
        >
          {validProductionOffices.map((officeName, idx) => (
            <MenuItem key={idx} value={officeName}>
              {productionOfficeNames[idx]}
            </MenuItem>
          ))}
        </Select>
      </div>
    </div>
  );
};

export default ProductionOfficeComponent;
