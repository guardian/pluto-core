import React from "react";
import { Button, Grid, Typography } from "@material-ui/core";
import { CancelOutlined, Delete } from "@material-ui/icons";
import { useHistory } from "react-router-dom";

interface ImprovedDeleteComponentProps {
  itemClass: string;
  deleteConfirmed: () => void;
}

const ImprovedDeleteComponent: React.FC<ImprovedDeleteComponentProps> = (
  props
) => {
  const history = useHistory();

  return (
    <div className="filter-list-block">
      <Typography variant="h3">Delete {props.itemClass}</Typography>
      <Typography>
        The following {props.itemClass} will be PERMANENTLY deleted, if you
        click the Delete button below. Do you want to continue?
      </Typography>
      {props.children}
      <Grid container direction="row" justify="space-between">
        <Grid item>
          <Button
            variant="outlined"
            startIcon={<CancelOutlined />}
            onClick={() => history.goBack()}
          >
            Cancel
          </Button>
        </Grid>
        <Grid item>
          <Button
            variant="contained"
            endIcon={<Delete />}
            onClick={props.deleteConfirmed}
          >
            Delete
          </Button>
        </Grid>
      </Grid>
    </div>
  );
};

export default ImprovedDeleteComponent;
