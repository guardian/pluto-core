import React, { useState } from "react";
import CommonMultistepContainer from "./common/CommonMultistepContainer";
import PlutoLinkageComponent from "./commissioncreate_new/PlutoLinkageComponent";

interface CommissionMultistepNewProps {
  itemId?: number;
}

const CommissionMultistepNew: React.FC<CommissionMultistepNewProps> = (
  props
) => {
  const [activeStep, setActiveStep] = useState(0);
  const [creationInProgress, setCreationInProgress] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | undefined>(
    undefined
  );

  const [workingGroupId, setWorkingGroupId] = useState<number | undefined>(
    undefined
  );

  const steps = ["Working group", "Title", "Production Office", "Review"];

  const canComplete = () => {
    return false;
  };

  const doCreate = async () => {
    alert("Creation not implemented yet");
  };

  return (
    <CommonMultistepContainer
      activeStep={activeStep}
      title="Create a commission - Pluto"
      id="commission-multistep"
      setActiveStep={setActiveStep}
      steps={steps}
      creationInProgress={false}
      creationFailed={errorMessage}
      canComplete={canComplete}
      createClicked={doCreate}
    >
      {activeStep == 0 ? (
        <PlutoLinkageComponent
          workingGroupIdDidChange={setWorkingGroupId}
          workingGroupId={workingGroupId}
        />
      ) : undefined}
    </CommonMultistepContainer>
  );
};

export default CommissionMultistepNew;
