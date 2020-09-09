import React from "react";
import {
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
} from "@material-ui/core";
import { validProductionOffices } from "../utils/constants";

interface ProductionOfficeSelectorProps {
  label: string;
  value: string;
  onChange: (evt: any) => void;
}

const ProductionOfficeSelector: React.FC<ProductionOfficeSelectorProps> = (
  props
) => {
  return (
    <FormControl>
      <InputLabel htmlFor="working-group-selector">{props.label}</InputLabel>
      <Select
        id="working-group-selector"
        value={props.value}
        onChange={props.onChange}
      >
        {validProductionOffices.map((productionOffice) => (
          <MenuItem value={productionOffice} key={productionOffice}>
            {productionOffice}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
};

export default ProductionOfficeSelector;
