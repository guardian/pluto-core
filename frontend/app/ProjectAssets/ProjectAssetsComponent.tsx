import React from "react";
import { Button, Grid, Paper, Typography } from "@material-ui/core";
import { MediabrowserContextProvider } from "../mediabrowser-linkage/mediabrowser";
import { Alert } from "@material-ui/lab";
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
      <Paper elevation={3} style={{ padding: "1rem" }}>
        <Typography variant="h4">Project Media</Typography>
        <Grid container direction="row">
          <Grid
            item
            style={{ width: "10%", minWidth: "150px", overflow: "hidden" }}
          >
            <AssetFolderLink projectId={this.props.projectid} />
          </Grid>
          <Grid
            item
            style={{ width: "90%", overflowY: "hidden", overflowX: "scroll" }}
          >
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
