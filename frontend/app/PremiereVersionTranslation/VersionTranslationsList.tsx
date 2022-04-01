import React, { useState } from "react";
import { RouteComponentProps } from "react-router";
import { Helmet } from "react-helmet";
import { Grid, IconButton, Paper, Tooltip } from "@material-ui/core";
import { Add } from "@material-ui/icons";

const VersionTranslationsList: React.FC<RouteComponentProps> = (props) => {
  const [knownTranslations, setKnownTranslations] = useState<
    PremiereVersionTranslation[]
  >([]);

  const addNew = () => {};

  return (
    <>
      <Helmet>
        <title>Premiere version translations - Pluto admin</title>
      </Helmet>
      <Grid
        container
        spacing={3}
        justifyContent="space-around"
        alignContent="flex-end"
      >
        <Grid item>
          <Tooltip title="Add a new version translation">
            <IconButton onClick={addNew}>
              <Add />
            </IconButton>
          </Tooltip>
        </Grid>
      </Grid>
      <Paper elevation={3}></Paper>
    </>
  );
};
