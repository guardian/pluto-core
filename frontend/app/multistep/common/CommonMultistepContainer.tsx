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
  createButtonLabel?: string;
}

const multistepStyles = makeStyles((theme) => ({
  stepContainer: {
    width: "fit-content",
    padding: "3em",
    paddingTop: "0.5em",
    paddingBottom: "1em",
    marginLeft: "auto",
    marginRight: "auto",
    marginTop: "3em",
  },
  warning: {
    color: theme.palette.warning.main,
  },
  information: {
    color: theme.palette.info.main,
    fontSize: "0.8em",
    fontStyle: "italic",
  },
  labelCell: {
    verticalAlign: "bottom",
    width: "25%",
  },
  fullWidth: {
    width: "100%",
  },
  valueNotPresent: {
    color: theme.palette.grey.A700,
  },
  stepper: {
    backgroundColor: "#00000000",
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
      <Stepper activeStep={activeStep} className={classes.stepper}>
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
        <Grid justifyContent="space-between" container>
          <Grid item>
            <Button
              variant="outlined"
              disabled={activeStep == 0 || creationInProgress || activeStep > 6}
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
                <span>
                  {" "}
                  {/* the <span> wrapper is required to get mouseover events when the button is in a "disabled" state*/}
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
                    {props.createButtonLabel ?? "Create"}
                  </Button>
                </span>
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
