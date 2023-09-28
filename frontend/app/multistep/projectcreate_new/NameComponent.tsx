import React, { ChangeEvent, useContext, useEffect, useState } from "react";
import {
  CircularProgress,
  Grid,
  Input,
  Switch,
  Typography,
} from "@material-ui/core";
import { format } from "date-fns";
import UserContext from "../../UserContext";
import axios from "axios";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import StorageSelector from "../../Selectors/StorageSelector";
import { getProjectsDefaultStorageId } from "./ProjectStorageService";
import { useGuardianStyles } from "~/misc/utils";
import { isLoggedIn } from "~/utils/api";
import ObituaryComponent from "./ObituaryComponent";
import ProductionOfficeComponent from "./ProductionOfficeComponent";

interface NameComponentProps {
  projectName: string;
  projectNameDidChange: (newValue: string) => void;
  fileName: string;
  fileNameDidChange: (newValue: string) => void;
  selectedStorageId: number | undefined;
  storageIdDidChange: (newValue: number) => void;

  valueDidChange: (newValue: string) => void;
  checkBoxDidChange: (newValue: boolean) => void;
  value: string;
  isObituary: boolean;

  valueWasSet: (newValue: ProductionOffice) => void;
  productionOfficeValue: string;
  extraText?: string;
}

const NameComponent: React.FC<NameComponentProps> = (props) => {
  const [autoName, setAutoName] = useState(true);
  const [knownStorages, setKnownStorages] = useState<PlutoStorage[]>([]);
  const [loading, setLoading] = useState(false);
  const classes = useGuardianStyles();

  const userContext = useContext(UserContext);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);

  const loadStorages = async () => {
    setLoading(true);
    const response = await axios.get<PlutoStorageListResponse>("/api/storage", {
      validateStatus: () => true,
    });
    switch (response.status) {
      case 200:
        setKnownStorages(response.data.result);
        setLoading(false);
        break;
      default:
        setLoading(false);
        console.error(
          `Could not load in storages: ${response.status} ${response.statusText}`,
          response.data
        );
        SystemNotification.open(
          SystemNotifcationKind.Error,
          `Could not load in storages, server error ${response.status}. More details in the browser console.`
        );
    }
  };

  const makeAutoFilename = (title: string) => {
    const sanitizer = /[^\w\d_]+/g;
    return (
      format(new Date(), "yyyyMMdd") +
      "_" +
      title.substring(0, 32).replace(sanitizer, "_").toLowerCase()
    );
  };

  useEffect(() => {
    if (autoName) {
      props.fileNameDidChange(makeAutoFilename(props.projectName));
    }
  }, [props.projectName]);

  useEffect(() => {
    loadStorages();
  }, []);

  useEffect(() => {
    if (!props.selectedStorageId && knownStorages.length) {
      getProjectsDefaultStorageId()
        .then((id) => props.storageIdDidChange(id))
        .catch((error) => {
          console.error("Could not get default storage id: ", error);
          if (error.response && error.response.status === 404) {
            SystemNotification.open(
              SystemNotifcationKind.Error,
              "No default project storage has been set"
            );
          } else if (!error.hasOwnProperty("response")) {
            SystemNotification.open(
              SystemNotifcationKind.Error,
              "Could not understand response for default storage, check the console"
            );
          } else {
            SystemNotification.open(
              SystemNotifcationKind.Error,
              "Server error loading the default storage id, please try again in a couple of minutes"
            );
          }
          props.storageIdDidChange(knownStorages[0].id);
        });
    }
  }, [knownStorages]);

  const fetchWhoIsLoggedIn = async () => {
    try {
      const loggedIn = await isLoggedIn();
      setIsAdmin(loggedIn.isAdmin);
    } catch {
      setIsAdmin(false);
    }
  };

  fetchWhoIsLoggedIn();

  return (
    <div className={classes.common_box_size}>
      <Typography variant="h3">Project configuration</Typography>
      {/* <Typography>
        Now, we need a descriptive name for your new project
      </Typography> */}
      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Typography>Project Name</Typography>
          <Input
            className={classes.inputBox}
            id="projectNameInput"
            onChange={(event) => props.projectNameDidChange(event.target.value)}
            placeholder="Type a good descriptive project name here"
            value={props.projectName}
          />
        </Grid>
        {console.log("isAdmin: ", isAdmin)}
        {!isAdmin && (
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={6}>
              <Typography>File name</Typography>
            </Grid>
            <Grid item xs={6}>
              <Input
                className={classes.inputBox}
                id="fileNameInput"
                onChange={(event) =>
                  props.fileNameDidChange(event.target.value)
                }
                value={props.fileName}
                disabled={autoName}
              />
            </Grid>
            <Grid item xs={6}>
              <Typography>Automatically name file (recommended)</Typography>
            </Grid>
            <Grid item xs={6}>
              <Switch
                id="autoNameCheck"
                checked={autoName}
                onChange={(event) => setAutoName(event.target.checked)}
              />
            </Grid>
            <Grid item xs={6}>
              <Typography>Project storage</Typography>
            </Grid>
            <Grid item xs={6}>
              <Grid container direction="row" alignItems="center">
                <Grid item>
                  <StorageSelector
                    storageList={knownStorages}
                    enabled={userContext?.isAdmin}
                    selectionUpdated={(newValue: number) =>
                      props.storageIdDidChange(newValue)
                    }
                    selectedStorage={props.selectedStorageId}
                  />
                  {!userContext?.isAdmin && (
                    <Typography className={classes.secondary}>
                      Only administrators can change the project storage
                      location
                    </Typography>
                  )}
                </Grid>
                <Grid item>
                  {loading && <CircularProgress style={{ height: "0.8em" }} />}
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        )}
        <Grid item xs={12}>
          <ObituaryComponent
            valueDidChange={props.valueDidChange}
            value={props.value}
            isObituary={props.isObituary}
            checkBoxDidChange={props.checkBoxDidChange}
          />
        </Grid>
        <Grid item xs={12}>
          <ProductionOfficeComponent
            productionOfficeValue={props.productionOfficeValue}
            valueWasSet={props.valueWasSet}
          />
        </Grid>
      </Grid>
    </div>
  );
};

export default NameComponent;
