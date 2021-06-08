import React, { useContext, useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
import {
  Button,
  Grid,
  makeStyles,
  Paper,
  Step,
  StepLabel,
  Stepper,
  Tooltip,
  Typography,
} from "@material-ui/core";
import NameComponent from "./projectcreate_new/NameComponent";
import TemplateComponent from "./projectcreate_new/TemplateComponent";
import { CheckCircle } from "@material-ui/icons";
import UserContext from "../UserContext";
import PlutoLinkageComponent from "./projectcreate_new/PlutoLinkageComponent";
import { Helmet } from "react-helmet";
import ProductionOfficeComponent from "./projectcreate_new/ProductionOfficeComponent";
import MediaRulesComponent from "./projectcreate_new/MediaRulesComponent";
import SummaryComponent from "./projectcreate_new/SummaryComponent";

const useStyles = makeStyles((theme) => ({
  stepContainer: {
    width: "fit-content",
    padding: "3em",
    paddingTop: "0.2em",
    marginLeft: "auto",
    marginRight: "auto",
  },
}));

interface StepContentProps {
  activeStep: number;
  children: React.ReactNode;
  className?: string;
}

interface StepContentState {
  didError: boolean;
}

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

const ProjectCreateMultistepNew: React.FC<RouteComponentProps> = (props) => {
  const classes = useStyles();
  const context = useContext(UserContext);

  const [activeStep, setActiveStep] = useState(0);
  const [skipped, setSkipped] = React.useState(new Set<number>());

  const [projectName, setProjectName] = useState("");
  const [filename, setFilename] = useState("");

  const [selectedStorageId, setSelectedStorageId] = useState<
    number | undefined
  >(undefined);

  const [selectedTemplateId, setSelectedTemplateId] = useState<
    number | undefined
  >(undefined);

  const [workingGroupId, setWorkingGroupId] = useState<number | undefined>(
    undefined
  );
  const [commissionId, setCommissionId] = useState<number | undefined>(
    undefined
  );
  const [productionOffice, setProductionOffice] = useState("UK");
  const [deletable, setDeletable] = useState(false);
  const [deepArchive, setDeepArchive] = useState(true);
  const [sensitive, setSensitive] = useState(false);

  const steps = [
    "Select project template",
    "Name your project",
    "Working Group & Commission",
    "Production Office",
    "Media Rules",
    "Review",
  ];

  useEffect(() => {
    console.log("User context changed, new value: ", context);
    if (projectName == "") {
      setProjectName(
        context?.userName ? `${context.userName}'s project` : "My project"
      );
    }
  }, [context]);

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

  /**
   * returns true if we are in a position to create a project without issues
   */
  const canComplete = () => {
    return (
      projectName != "" &&
      projectName != "My project" &&
      filename != "" &&
      selectedTemplateId &&
      selectedStorageId &&
      workingGroupId &&
      commissionId &&
      productionOffice != "" &&
      (deletable || deepArchive)
    );
  };

  return (
    <div id="project-create-multistep">
      <Helmet>
        <title>Create an Edit - Pluto</title>
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
        <>
          {activeStep == 0 ? (
            <TemplateComponent
              valueDidChange={(newTemplate) =>
                setSelectedTemplateId(newTemplate)
              }
              value={selectedTemplateId}
            />
          ) : null}
          {activeStep == 1 ? (
            <NameComponent
              projectName={projectName}
              projectNameDidChange={(newName) => setProjectName(newName)}
              fileName={filename}
              fileNameDidChange={(newName) => setFilename(newName)}
              selectedStorageId={selectedStorageId}
              storageIdDidChange={(newValue) => setSelectedStorageId(newValue)}
            />
          ) : null}
          {activeStep == 2 ? (
            <PlutoLinkageComponent
              commissionIdDidChange={(newValue) => setCommissionId(newValue)}
              workingGroupIdDidChange={(newValue) =>
                setWorkingGroupId(newValue)
              }
              commissionId={commissionId}
              workingGroupId={workingGroupId}
            />
          ) : null}
          {activeStep == 3 ? (
            <ProductionOfficeComponent
              valueWasSet={(newValue) => setProductionOffice(newValue)}
              value={productionOffice}
            />
          ) : null}
          {activeStep == 4 ? (
            <MediaRulesComponent
              deletable={deletable}
              deepArchive={deepArchive}
              sensitive={sensitive}
              archivalChanged={(newDeletable, newDeepArchive) => {
                setDeletable(newDeletable);
                setDeepArchive(newDeepArchive);
              }}
              sensitiveChanged={(newValue) => setSensitive(newValue)}
            />
          ) : null}
          {activeStep == 5 ? (
            <SummaryComponent
              projectName={projectName}
              fileName={filename}
              projectTemplateId={selectedTemplateId}
              destinationStorageId={selectedStorageId}
              workingGroupId={workingGroupId}
              productionOffice={productionOffice}
              commissionId={commissionId}
              deletable={deletable}
              deepArchive={deepArchive}
              sensitive={sensitive}
            />
          ) : null}
        </>
        <hr />
        <Grid justify="space-between" container>
          <Grid item>
            <Button
              variant="outlined"
              disabled={activeStep == 0}
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
                    ? "Create the project"
                    : "You need to supply some more information, check above for details"
                }
              >
                <Button
                  variant="contained"
                  disabled={!canComplete()}
                  endIcon={<CheckCircle />}
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

export default ProjectCreateMultistepNew;
