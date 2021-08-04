import React from "react";
import { Button, Grid, Paper, Typography } from "@material-ui/core";
import { MediabrowserContextProvider } from "../mediabrowser-linkage/mediabrowser";
import { Alert } from "@material-ui/lab";
import { Launch } from "@material-ui/icons";
import AssetFolderLink from "../ProjectEntryList/AssetFolderLink";

interface ProjectAssetsComponentProps {
  projectid: number;
}

interface ProjectAssetsComponentState {
  didError: boolean;
}

/**
 * parent contained for the project assets component that catches errors and displays the title/container
 */
class ProjectAssetsComponent extends React.Component<
  ProjectAssetsComponentProps,
  ProjectAssetsComponentState
> {
  constructor(props: ProjectAssetsComponentProps) {
    super(props);

    this.state = {
      didError: false,
    };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("ProjectAssetsComponent crashed: ", error);
    this.setState({ didError: true });
  }

  static getDerivedStateFromError(error: any) {
    // Update state so the next render will show the fallback UI.
    return { didError: true };
  }

  render() {
    return (
      <Paper elevation={3}>
        <Typography variant="h4">Project Media</Typography>
        <Grid container direction="column">
          <Grid item style={{ maxWidth: "100px" }}>
            <AssetFolderLink projectId={this.props.projectid} />
          </Grid>
          <Grid item>
            <MediabrowserContextProvider>
              {this.state.didError ? (
                <Alert severity="error">
                  The media browser component crashed, this should not happen.
                  Please report to multimediatech.
                </Alert>
              ) : (
                this.props.children
              )}
            </MediabrowserContextProvider>
          </Grid>
        </Grid>
      </Paper>
    );
  }
}

export default ProjectAssetsComponent;
