import React from "react";
import { Helmet } from "react-helmet";
import {
  Button,
  Grid,
  makeStyles,
  Step,
  StepLabel,
  Stepper,
  Tooltip,
  Typography,
} from "@material-ui/core";
import StepContent from "./StepContent";
import { CheckCircle } from "@material-ui/icons";

interface CommonMultistepContainerProps {
  activeStep: number;
  title: string;
  id: string | undefined;
  setActiveStep: (newValue: number | ((prevValue: number) => number)) => void;
  steps: string[];
  creationInProgress: boolean | undefined;
  creationFailed: string | undefined;
  canComplete: () => boolean | 0 | undefined;
  createClicked: () => Promise<void>;
}

const multistepStyles = makeStyles((theme) => ({
  stepContainer: {
    width: "fit-content",
    padding: "3em",
    paddingTop: "0.2em",
    marginLeft: "auto",
    marginRight: "auto",
  },
  warning: {
    color: theme.palette.warning.main,
  },
}));

const CommonMultistepContainer: React.FC<CommonMultistepContainerProps> = (
  props
) => {
  const [skipped, setSkipped] = React.useState(new Set<number>());

  const {
    activeStep,
    setActiveStep,
    steps,
    creationInProgress,
    creationFailed,
    canComplete,
    createClicked,
  } = props;

  const classes = multistepStyles();

  const isStepSkipped = (step: number) => {
    return skipped.has(step);
  };

  const handleNext = () => {
    let newSkipped = skipped;
    if (isStepSkipped(activeStep)) {
      newSkipped = new Set(newSkipped.values());
      newSkipped.delete(activeStep);
    }

    setActiveStep((prevActiveStep) => prevActiveStep + 1);
    setSkipped(newSkipped);
  };

  const handleBack = () => {
    setActiveStep((prevActiveStep) => prevActiveStep - 1);
  };

  return (
    <div id={props.id}>
      <Helmet>
        <title>{props.title}</title>
      </Helmet>
      <Stepper activeStep={activeStep}>
        {steps.map((label, index) => {
          const stepProps: { completed?: boolean } = {};
          const labelProps: { optional?: React.ReactNode } = {};
          if (isStepSkipped(index)) {
            stepProps.completed = false;
          }
          return (
            <Step key={label} {...stepProps}>
              <StepLabel {...labelProps}>{label}</StepLabel>
            </Step>
          );
        })}
      </Stepper>

      <StepContent activeStep={activeStep} className={classes.stepContainer}>
        {props.children}
        <hr />
        <Grid justify="space-between" container>
          <Grid item>
            <Button
              variant="outlined"
              disabled={activeStep == 0 || creationInProgress || activeStep > 5}
              onClick={handleBack}
            >
              Back
            </Button>
          </Grid>
          <Grid item>
            {activeStep >= steps.length - 1 ? (
              <Tooltip
                title={
                  canComplete()
                    ? "Go ahead and create"
                    : "You need to supply some more information, check above for details"
                }
              >
                <Button
                  variant="contained"
                  disabled={
                    !canComplete() ||
                    creationInProgress ||
                    creationFailed !== undefined ||
                    activeStep > 6
                  }
                  endIcon={<CheckCircle />}
                  onClick={createClicked}
                >
                  Create
                </Button>
              </Tooltip>
            ) : (
              <Button variant="contained" onClick={handleNext}>
                Next
              </Button>
            )}
          </Grid>
        </Grid>
      </StepContent>
    </div>
  );
};

export { multistepStyles };
export default CommonMultistepContainer;
