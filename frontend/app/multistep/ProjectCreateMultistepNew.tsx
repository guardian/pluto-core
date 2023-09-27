import React, { useContext, useEffect, useState } from "react";
import { RouteComponentProps, useLocation } from "react-router-dom";
import { Typography } from "@material-ui/core";
import NameComponent from "./projectcreate_new/NameComponent";
import TemplateComponent from "./projectcreate_new/TemplateComponent";
import UserContext from "../UserContext";
import PlutoLinkageComponent from "./projectcreate_new/PlutoLinkageComponent";
import ProductionOfficeComponent from "./projectcreate_new/ProductionOfficeComponent";
import MediaRulesComponent from "./projectcreate_new/MediaRulesComponent";
import SummaryComponent from "./projectcreate_new/SummaryComponent";
import InProgressComponent from "./projectcreate_new/InProgressComponent";
import { CreateProject } from "./projectcreate_new/CreationAction";
import ProjectCreatedComponent from "./projectcreate_new/ProjectCreatedComponent";
import { Link } from "react-router-dom";
import CommonMultistepContainer from "./common/CommonMultistepContainer";
import ObituaryComponent from "./projectcreate_new/ObituaryComponent";
import { useGuardianStyles } from "~/misc/utils";

const ProjectCreateMultistepNew: React.FC<RouteComponentProps> = (props) => {
  const context = useContext(UserContext);

  const [activeStep, setActiveStep] = useState(0);

  const [projectName, setProjectName] = useState("");
  const [filename, setFilename] = useState("");
  const [isObituary, setObituary] = useState(false);
  const [obituaryName, setObituaryName] = useState<null | string>(null);

  useEffect(() => {
    // Clear obituary name if the user deselects the *Is this an obituary?*

    if (!isObituary) {
      setObituaryName(null);
    }
  }, [isObituary]);

  const [creationInProgress, setCreationInProgress] = useState<
    boolean | undefined
  >(undefined);
  const [creationFailed, setCreationFailed] = useState<string | undefined>(
    undefined
  );

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
  const [productionOffice, setProductionOffice] = useState<ProductionOffice>(
    "UK"
  );
  const [deletable, setDeletable] = useState(false);
  const [deepArchive, setDeepArchive] = useState(true);
  const [sensitive, setSensitive] = useState(false);
  const [createdProjectId, setCreatedProjectId] = useState<undefined | number>(
    undefined
  );

  const location = useLocation();
  const userContext = useContext(UserContext);
  const classes = useGuardianStyles();

  const steps = [
    "Select project template",
    "Name your project",
    "Obituary",
    "Working Group & Commission",
    "Production Office",
    "Media Rules",
    "Review",
  ];

  useEffect(() => {
    console.log("location updated: ", location);
    const query = new URLSearchParams(location.search);
    const urlCommId = query.get("commissionId");
    if (urlCommId) {
      try {
        setCommissionId(parseInt(urlCommId));
      } catch (err) {
        console.log("invalid commission id passed in: ", urlCommId, err);
      }
    }

    const urlWGId = query.get("workingGroupId");
    if (urlWGId) {
      try {
        setWorkingGroupId(parseInt(urlWGId));
      } catch (err) {
        console.log("invalid working group id passed in: ", urlWGId, err);
      }
    }
  }, [location]);

  const createClicked = async () => {
    setActiveStep(7);
    setCreationInProgress(true);
    setCreationFailed(undefined);

    const result = await CreateProject({
      filename: filename,
      title: projectName,
      obitProject: obituaryName ?? null,
      destinationStorageId: selectedStorageId as number,
      projectTemplateId: selectedTemplateId as number,
      user: userContext?.userName ?? "unknown",
      workingGroupId: workingGroupId as number,
      commissionId: commissionId as number,
      deletable: deletable,
      deepArchive: deepArchive,
      sensitive: sensitive,
      productionOffice: productionOffice,
    });

    if (result.createdOk) {
      if (result.projectId) setCreatedProjectId(result.projectId);
      setCreationInProgress(false);
      setCreationFailed(undefined);
      setActiveStep((prev) => prev + 1);
    } else {
      setCreationFailed(result.errorMessage);
      setCreationInProgress(false);

      if (result.shouldRetry) {
        window.setTimeout(() => createClicked(), 2000);
      }
    }
  };

  useEffect(() => {
    if (creationInProgress === false && !creationFailed) {
      console.log("Project created OK");
    }
  }, [creationInProgress, creationFailed]);

  useEffect(() => {
    console.log("User context changed, new value: ", context);
  }, [context]);

  /**
   * returns true if we are in a position to create a project without issues
   */
  const canComplete = () => {
    return (
      projectName != "" &&
      projectName != "My project" &&
      projectName != `${userContext?.userName}'s project` &&
      filename != "" &&
      selectedTemplateId &&
      selectedStorageId &&
      workingGroupId &&
      commissionId &&
      //@ts-ignore
      productionOffice != "" &&
      (deletable || deepArchive)
    );
  };

  return (
    <CommonMultistepContainer
      activeStep={activeStep}
      setActiveStep={setActiveStep}
      steps={steps}
      id="project-create-multistep"
      title="Create an Edit - Pluto"
      creationInProgress={creationInProgress}
      creationFailed={creationFailed}
      canComplete={canComplete}
      createClicked={createClicked}
      obituaryName={obituaryName}
      isObituary={isObituary}
    >
      <>
        {activeStep == 0 ? (
          <TemplateComponent
            valueDidChange={(newTemplate) => setSelectedTemplateId(newTemplate)}
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
          <ObituaryComponent
            valueDidChange={(newValue: string) => setObituaryName(newValue)}
            checkBoxDidChange={(newValue: boolean) => setObituary(newValue)}
            value={obituaryName ?? ""}
            isObituary={isObituary}
          />
        ) : null}
        {activeStep == 3 ? (
          <PlutoLinkageComponent
            commissionIdDidChange={(newValue) => setCommissionId(newValue)}
            workingGroupIdDidChange={(newValue) => setWorkingGroupId(newValue)}
            commissionId={commissionId}
            workingGroupId={workingGroupId}
          />
        ) : null}

        {activeStep == 4 ? (
          <ProductionOfficeComponent
            valueWasSet={(newValue) => setProductionOffice(newValue)}
            value={productionOffice}
          />
        ) : null}
        {activeStep == 5 ? (
          <MediaRulesComponent
            deletable={deletable}
            deepArchive={deepArchive}
            sensitive={sensitive}
            archivalChanged={(newDeletable, newDeepArchive) => {
              setDeletable(newDeletable);
              setDeepArchive(newDeepArchive);
            }}
            isObituary={isObituary}
            sensitiveChanged={(newValue) => setSensitive(newValue)}
          />
        ) : null}
        {activeStep == 6 ? (
          <SummaryComponent
            projectName={projectName}
            fileName={filename}
            isObituary={isObituary}
            obituaryName={obituaryName}
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
        {activeStep == 7 ? (
          <InProgressComponent
            didFail={creationFailed !== undefined}
            errorMessage={creationFailed}
            description="Creating your project, please wait..."
          />
        ) : null}
        {activeStep == 8 && createdProjectId && commissionId ? (
          <ProjectCreatedComponent
            projectId={createdProjectId}
            commissionId={commissionId}
            title={projectName}
          />
        ) : null}
        {activeStep == 8 && (!createdProjectId || !commissionId) ? (
          <div>
            <Typography className={classes.warning} variant="h3">
              Well this is strange
            </Typography>
            <Typography>
              It appears that your project was created, but I can't find the
              project ID so I can't show you the options screen I normally
              would. You'll just have to go back to the Projects list and find
              it from there.
            </Typography>
            <Link to="/project?mine">Go to the Projects list</Link>
          </div>
        ) : null}
      </>
    </CommonMultistepContainer>
  );
};

export default ProjectCreateMultistepNew;
