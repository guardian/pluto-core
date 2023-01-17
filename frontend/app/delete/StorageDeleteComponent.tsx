import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
import axios from "axios";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import ImprovedDeleteComponent from "./ImprovedDeleteComponent";
import { CircularProgress } from "@material-ui/core";
import SummaryComponent from "../multistep/storage/SummaryComponent";

const StorageDeleteComponent: React.FC<
  RouteComponentProps<{
    itemid?: string;
  }>
> = (props) => {
  const emptyLoginDetails: StorageLoginDetails = {
    hostname: "",
    port: 0,
    device: "",
    username: "",
    password: "",
  };

  const emptyStorageType: StorageType = {
    name: "",
    needsLogin: true,
    canVersion: true,
    hasSubFolders: true,
  };

  const [storageData, setStorageData] = useState<StorageEntry | undefined>(
    undefined
  );
  const [storageType, setStorageType] = useState<StorageType>(emptyStorageType);
  const [isLoading, setIsLoading] = useState(true);
  const [loginDetails, setLoginDetails] =
    useState<StorageLoginDetails>(emptyLoginDetails);

  const loadData = async () => {
    if (props.match.params.itemid) {
      try {
        const numericValue = parseInt(props.match.params.itemid);
        const response = await axios.get<PlutoApiResponse<StorageEntry>>(
          `/api/storage/${numericValue}`
        );
        setStorageData(response.data.result);
        setIsLoading(false);
      } catch (err) {
        console.error(
          "Could not load storage data for id ",
          props.match.params.itemid,
          ": ",
          err
        );
        SystemNotification.open(
          SystemNotifcationKind.Error,
          "Could not load storage data"
        );
        props.history.goBack();
      }
    }
  };

  const doDelete = async () => {
    if (props.match.params.itemid) {
      try {
        const numericValue = parseInt(props.match.params.itemid);
        await axios.delete(`/api/storage/${numericValue}`);
        props.history.goBack();
      } catch (err) {
        console.error("Could not delete storage: ", err);
        SystemNotification.open(
          SystemNotifcationKind.Error,
          `Could not delete storage: ${err}`
        );
      }
    }
  };

  useEffect(() => {
    loadData();
  }, [props.match.params.itemid]);

  useEffect(() => {
    const value: StorageLoginDetails = {
      hostname: storageData?.host ?? "",
      port: storageData?.port ?? 0,
      device: storageData?.device ?? "",
      username: storageData?.user ?? "",
      password: storageData?.password ?? "",
    };
    if (
      value.hostname != "" ||
      value.port != 0 ||
      value.device != "" ||
      value.username != "" ||
      value.password != ""
    ) {
      setLoginDetails(value);
    }
  }, [storageData]);

  useEffect(() => {
    const value: StorageType = {
      name: storageData?.storageType ?? "",
      canVersion: true,
      hasSubFolders: true,
      needsLogin: true,
    };
    setStorageType(value);
  }, [storageData]);

  return (
    <ImprovedDeleteComponent itemClass="storage" deleteConfirmed={doDelete}>
      {isLoading ? (
        <CircularProgress />
      ) : (
        <SummaryComponent
          storageType={storageType}
          loginDetails={loginDetails}
          rootPath={storageData?.rootpath ?? ""}
          clientPath={storageData?.clientpath ?? ""}
          enableVersions={storageData?.supportsVersion ?? false}
          backsUpTo={storageData?.backsUpTo}
          nickName={storageData?.nickname ?? ""}
        />
      )}
    </ImprovedDeleteComponent>
  );
};

export default StorageDeleteComponent;
