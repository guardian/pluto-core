import React from "react";
import { Paper, Typography } from "@material-ui/core";

interface StepContentProps {
  activeStep: number;
  children: React.ReactNode;
  className?: string;
}

interface StepContentState {
  didError: boolean;
}

/**
 * this component forms an error boundary and visual border around the contained children,
 * for use as a container for multistep forms
 */
class StepContent extends React.Component<StepContentProps, StepContentState> {
  constructor(props: StepContentProps) {
    super(props);

    this.state = {
      didError: false,
    };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("Multistep step failed: ", error);
    console.error("Extra react info: ", errorInfo);
  }

  componentDidUpdate(
    prevProps: Readonly<StepContentProps>,
    prevState: Readonly<StepContentState>,
    snapshot?: any
  ) {
    if (prevProps.activeStep !== this.props.activeStep && this.state.didError) {
      this.setState({ didError: false });
    }
  }

  static getDerivedStateFromError(error: any): StepContentState {
    return {
      didError: true,
    };
  }

  render() {
    if (this.state.didError) {
      return (
        <Paper elevation={3} className={this.props.className}>
          <Typography variant="h4">Error</Typography>
          <Typography>
            An internal error occurred in this component and has been logged to
            the browser console.
          </Typography>
          <Typography>
            Please report this to multimediatech@theguardian.com.
          </Typography>
        </Paper>
      );
    }

    return (
      <Paper elevation={3} className={this.props.className}>
        {this.props.children}
      </Paper>
    );
  }
}

export default StepContent;
