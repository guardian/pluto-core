import React from "react";
import { MenuItem, Select, TextField, Typography } from "@material-ui/core";
import { projectCreateStyles } from "./CommonStyles";
import { useGuardianStyles } from "~/misc/utils";

interface ProductionOfficeComponentProps {
  valueWasSet: (newValue: ProductionOffice) => void;
  productionOfficeValue: string;
  extraText?: string;
}

const ProductionOfficeComponent: React.FC<ProductionOfficeComponentProps> = (
  props
) => {
  const classes = projectCreateStyles();
  const guardianClasses = useGuardianStyles();

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
      <div>
        <TextField
          select
          label="Production Office"
          helperText="Production office you are working from"
          value={props.productionOfficeValue}
          onChange={(evt) =>
            props.valueWasSet(evt.target.value as ProductionOffice)
          }
        >
          {validProductionOffices.map((officeName, idx) => (
            <MenuItem key={idx} value={officeName}>
              {productionOfficeNames[idx]}
            </MenuItem>
          ))}
        </TextField>
      </div>
    </div>
  );
};

export default ProductionOfficeComponent;
