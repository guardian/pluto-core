import React from "react";
import { Helmet } from "react-helmet";
import {
  Button,
  Grid,
  Step,
  StepLabel,
  Stepper,
  Tooltip,
  makeStyles,
  useMediaQuery,
  useTheme,
} from "@material-ui/core";
import StepContent from "./StepContent";
import { CheckCircle } from "@material-ui/icons";
import { useGuardianStyles } from "~/misc/utils";

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
  isObituary?: boolean;
  obituaryName?: string | null;
}

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
    isObituary,
    obituaryName,
  } = props;

  const classes = useGuardianStyles();

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

  const theme = useTheme();
  const isSmallScreen = useMediaQuery(theme.breakpoints.down("xs"));

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
        <Grid justifyContent="space-between" container wrap="wrap">
          {activeStep < 5 && (
            <>
              <Grid item>
                <Button
                  size={isSmallScreen ? "small" : "medium"}
                  variant="outlined"
                  disabled={
                    activeStep == 0 || creationInProgress || activeStep > 4
                  }
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
                        className={classes.createButtonHover}
                        size={isSmallScreen ? "small" : "medium"}
                        variant="contained"
                        disabled={
                          !canComplete() ||
                          creationInProgress ||
                          creationFailed !== undefined ||
                          activeStep > 4 ||
                          (isObituary && !obituaryName)
                        }
                        endIcon={<CheckCircle />}
                        onClick={createClicked}
                      >
                        {props.createButtonLabel ?? "Create"}
                      </Button>
                    </span>
                  </Tooltip>
                ) : (
                  <Button
                    variant="contained"
                    onClick={handleNext}
                    size={isSmallScreen ? "small" : "medium"}
                  >
                    Next
                  </Button>
                )}
              </Grid>
            </>
          )}
        </Grid>
      </StepContent>
    </div>
  );
};

export default CommonMultistepContainer;
