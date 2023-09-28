import React, { useContext, useState } from "react";
import CommonMultistepContainer from "./common/CommonMultistepContainer";
import PlutoLinkageComponent from "./commissioncreate_new/PlutoLinkageComponent";
import CommissionTitleComponent from "./commissioncreate_new/CommissionTitleComponent";
import add from "date-fns/add";
import ProductionOfficeComponent from "./projectcreate_new/ProductionOfficeComponent";
import SummaryComponent from "./commissioncreate_new/SummaryComponent";
import { formatISO, isAfter } from "date-fns";
import InProgressComponent from "./projectcreate_new/InProgressComponent";

import axios from "axios";
import UserContext from "../UserContext";
import CommissionCreated from "./commissioncreate_new/CommissionCreated";

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

  const [title, setTitle] = useState("");
  const [scheduledCompletion, setScheduledCompletion] = useState<Date>(
    add(new Date(), { weeks: 4 })
  );
  const [productionOffice, setProductionOffice] = useState<ProductionOffice>(
    "UK"
  );

  const [createdCommissionId, setCreatedCommissionId] = useState<
    number | undefined
  >(undefined);

  const steps = ["Working group", "Title", "Production Office", "Review"];

  const canComplete = () => {
    return (
      activeStep == 3 &&
      !!workingGroupId &&
      title != "" &&
      isAfter(scheduledCompletion, new Date())
    );
  };

  const userContext = useContext(UserContext);

  const doCreate = async () => {
    setActiveStep(4);
    setCreationInProgress(true);
    setErrorMessage(undefined);

    const createTime = formatISO(new Date());

    const requestContent = {
      title: title,
      status: "New",
      workingGroupId: workingGroupId,
      created: createTime,
      updated: createTime,
      productionOffice: productionOffice,
      scheduledCompletion: formatISO(scheduledCompletion),
      owner: userContext?.userName,
    };

    try {
      if (requestContent.owner == "" || requestContent.owner == undefined)
        throw "Could not determine username";

      const response = await axios.post(
        "/api/prexit/commission",
        requestContent,
        { validateStatus: () => true }
      );
      switch (response.status) {
        case 200:
          const content = response.data as GenericCreateResponse;
          setCreatedCommissionId(content.id);
          setActiveStep(6);
          setCreationInProgress(false);
          break;
        case 409:
          console.error("conflict error creating commission: ", response.data);
          setCreationInProgress(false);
          setErrorMessage(
            "Something else already exists. Try creating the commission with a different name."
          );
          break;
        case 400:
          console.error(
            "bad request error creating commission: ",
            response.data
          );
          setCreationInProgress(false);
          setErrorMessage(
            "One or more of the values you put in is not correct. If no warnings were shown please report this to multimediatech and try creating the commission again."
          );
          break;
        case 500:
          console.error("internal error creating commission: ", response.data);
          setCreationInProgress(false);
          setErrorMessage(
            "The server responded with an error, please try again in a couple of minutes"
          );
          break;
        case 502 | 503 | 504:
          console.warn("server unavailable while trying to create commission");
          setErrorMessage(
            "Server was unavailable when I tried to create the commission. Hold tight, i'll try again in a few seconds."
          );
          window.setTimeout(() => doCreate(), 3000);
          break;
        default:
          console.error(
            "unexpected return code from the server: ",
            response.status,
            " ",
            response.statusText
          );
          setErrorMessage(
            `I got an unexpected response code ${response.status} from the server. Please report this to multimediatech.`
          );
          break;
      }
    } catch (err) {
      console.error("could not create commission: ", err);
      setCreationInProgress(false);
      setErrorMessage(
        "An internal error occurred and the commission wasn't created.  Please try again."
      );
    }
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
      {activeStep == 1 ? (
        <CommissionTitleComponent
          title={title}
          expiration={scheduledCompletion}
          onTitleChanged={setTitle}
          onExpirationChanged={setScheduledCompletion}
        />
      ) : undefined}
      {activeStep == 2 ? (
        <ProductionOfficeComponent
          valueWasSet={(newValue) => setProductionOffice(newValue)}
          productionOfficeValue={productionOffice}
        />
      ) : undefined}
      {activeStep == 3 ? (
        <SummaryComponent
          title={title}
          scheduledCompetion={scheduledCompletion}
          workingGroupId={workingGroupId}
          productionOffice={productionOffice}
        />
      ) : undefined}
      {activeStep == 4 ? (
        <InProgressComponent
          didFail={errorMessage != undefined}
          description="Creating your commission, please wait..."
          errorMessage={errorMessage}
        />
      ) : undefined}
      {activeStep == 6 ? (
        createdCommissionId && workingGroupId ? (
          <CommissionCreated
            commissionId={createdCommissionId}
            workingGroupId={workingGroupId}
            title={title}
          />
        ) : (
          <InProgressComponent
            didFail={true}
            description=""
            errorMessage="Commission created but no commission ID and/or working group ID found. Please click 'Commissions' in the menu bar above to continue."
          />
        )
      ) : undefined}
    </CommonMultistepContainer>
  );
};

export default CommissionMultistepNew;
