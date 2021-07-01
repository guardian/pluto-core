import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
import CommonMultistepContainer from "./common/CommonMultistepContainer";
import StorageTypeComponent from "./storage/TypeComponent";
import axios from "axios";
import SystemNotification, {
  SystemNotificationKind,
} from "../SystemNotification";
import StorageLoginComponent from "./storage/LoginComponent";
import StorageSubfolderComponent from "./storage/SubfolderComponent";

interface StorageMultistepParams {
  itemId?: string;
}

const StorageMultistepNew: React.FC<RouteComponentProps<
  StorageMultistepParams
>> = (props) => {
  const [activeStep, setActiveStep] = useState(0);
  const [creationInProgress, setCreationInProgress] = useState(false);
  const [creationFailed, setCreationFailed] = useState<string | undefined>(
    undefined
  );

  const [strgTypes, setStrgTypes] = useState<StorageType[]>([]);
  const [selectedType, setSelectedType] = useState<number | undefined>(0);

  const [enableVersions, setEnableVersions] = useState(false);

  const [loginDetails, setLoginDetails] = useState<StorageLoginDetails>({
    hostname: "",
    port: 0,
    device: "",
    username: "",
    password: "",
  });

  const [rootpath, setRootpath] = useState("");
  const [clientpath, setClientpath] = useState("");

  const steps = [
    "Storage type",
    "Login details",
    "Subfolder location",
    "Confirm",
  ];

  //load in the known storage types at startup
  useEffect(() => {
    axios
      .get<StorageTypeResponse>("/api/storage/knowntypes")
      .then((result) => setStrgTypes(result.data.types))
      .catch((err) => {
        console.error("Could not load in storage types: ", err);
        SystemNotification.open(
          SystemNotificationKind.Error,
          `Could not load in storage types, see console for details`
        );
      });
  }, []);

  const findStorageType = (forName: string) => {
    for (let i = 0; i < strgTypes.length; i++) {
      if (strgTypes[i].name === forName) return i;
    }
    return 0;
  };

  //if a storage has been specified to edit, load in the data
  useEffect(() => {
    if (props.match.params.itemId == "new") {
      console.debug("creating new storage, nothing to load");
      return;
    }
    if (!strgTypes) {
      console.debug(
        "not loading in specified match until storage types available"
      );
      return;
    }

    axios
      .get<PlutoApiResponse<StorageEntry>>(
        `/api/storage/${props.match.params.itemId}`
      )
      .then((result) => {
        setSelectedType(findStorageType(result.data.result.storageType));
        setEnableVersions(result.data.result.supportsVersion);
        setLoginDetails({
          hostname: result.data.result.host ?? "",
          port: result.data.result.port ?? 0,
          device: result.data.result.device ?? "",
          username: result.data.result.user ?? "",
          password: result.data.result.password ?? "",
        });
      })
      .catch((err) => {
        console.error(
          `Could not load in existing storage '${props.match.params.itemId}: `,
          err
        );
        SystemNotification.open(
          SystemNotificationKind.Error,
          "Could not load in storage data, see browser console"
        );
      });
  }, [props.match.params, strgTypes]);

  useEffect(() => {
    if (strgTypes.length > 0) {
      setSelectedType(0);
    }
  }, [strgTypes]);

  //when the selected type is changed, toggle the default value for allowing versions
  useEffect(() => {
    if (selectedType && selectedType < strgTypes.length && selectedType >= 0) {
      const actualSelectedStorage = strgTypes[selectedType];
      setEnableVersions(actualSelectedStorage.canVersion);
    } else {
      console.log(
        `can't set versions as storage type index ${selectedType} is out of range`
      );
    }
  }, [selectedType]);

  const canComplete = () => {
    return false;
  };

  const createClicked = async () => {
    return Promise.reject<void>("Not implemented yet");
  };

  return (
    <CommonMultistepContainer
      activeStep={activeStep}
      title="Create a storage - Pluto Admin"
      id="storage-create-multistep"
      setActiveStep={setActiveStep}
      steps={steps}
      creationInProgress={creationInProgress}
      creationFailed={creationFailed}
      canComplete={canComplete}
      createClicked={createClicked}
    >
      <>
        {activeStep == 0 ? (
          <StorageTypeComponent
            strgTypes={strgTypes}
            selectedType={selectedType}
            versionsAllowed={enableVersions}
            valueWasSet={(type: number) => setSelectedType(type)}
            versionsAllowedChanged={(newValue: boolean) =>
              setEnableVersions(newValue)
            }
          />
        ) : undefined}
        {activeStep == 1 && selectedType != undefined ? (
          <StorageLoginComponent
            currentStorage={strgTypes[selectedType]}
            loginDetails={loginDetails}
            valueWasSet={(loginDetails) => setLoginDetails(loginDetails)}
          />
        ) : undefined}
        {activeStep == 2 && selectedType != undefined ? (
          <StorageSubfolderComponent
            rootPath={rootpath}
            clientPath={clientpath}
            currentStorage={strgTypes[selectedType]}
            rootPathWasSet={setRootpath}
            clientPathWasSet={setClientpath}
          />
        ) : undefined}
      </>
    </CommonMultistepContainer>
  );
};

export default StorageMultistepNew;
