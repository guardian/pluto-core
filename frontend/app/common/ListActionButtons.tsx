import React from "react";
import { RouteComponentProps } from "react-router-dom";
import { Grid, IconButton } from "@material-ui/core";
import EditIcon from "@material-ui/icons/Edit";
import DeleteIcon from "@material-ui/icons/Delete";

interface ListActionButtonsProps {
  itemId: number;
}

const ListActionButtons: React.FC<
  RouteComponentProps & ListActionButtonsProps
> = (props) => {
  const breakdownPathComponents = () => props.location.pathname.split("/");
  const componentName = breakdownPathComponents()[1];

  return (
    <Grid container className="icons">
      <Grid item>
        <IconButton
          onClick={() =>
            props.history.push(`/${componentName}/${props.itemId}`)
          }
        >
          <EditIcon />
        </IconButton>
      </Grid>
      <Grid item>
        <IconButton
          onClick={() =>
            props.history.push(`/${componentName}/${props.itemId}/delete`)
          }
        >
          <DeleteIcon />
        </IconButton>
      </Grid>
    </Grid>
  );
};

export default ListActionButtons;
