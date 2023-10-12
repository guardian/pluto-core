import React, { useEffect, useState } from "react";
import { Grid, Paper, Switch, TextField, Typography } from "@material-ui/core";
import { format } from "date-fns";
import { useGuardianStyles } from "~/misc/utils";
import { isLoggedIn } from "~/utils/api";
import ObituaryComponent from "./ObituaryComponent";
import ProductionOfficeComponent from "./ProductionOfficeComponent";
import TemplateComponent from "./TemplateComponent";
import { getProjectsDefaultStorageId } from "./ProjectStorageService";
import {
  SystemNotifcationKind,
  SystemNotification,
} from "@guardian/pluto-headers";
import axios from "axios";

interface ConfigurationComponentProps {
  templateValue?: number;
  templateValueDidChange: (newValue: number) => void;

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

const ConfigurationComponent: React.FC<ConfigurationComponentProps> = (
  props
) => {
  const [autoName, setAutoName] = useState(true);
  const [knownStorages, setKnownStorages] = useState<PlutoStorage[]>([]);
  const classes = useGuardianStyles();

  const [isAdmin, setIsAdmin] = useState<boolean>(false);

  const [loading, setLoading] = useState(false);

  const loadStorages = async () => {
    console.log("Loading in storages...");
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
    loadStorages();
  }, []);

  useEffect(() => {
    if (autoName) {
      props.fileNameDidChange(makeAutoFilename(props.projectName));
    }
  }, [props.projectName]);

  const fetchWhoIsLoggedIn = async () => {
    try {
      console.log("Checking if user is admin");
      const loggedIn = await isLoggedIn();
      console.log("loggedin data: ", loggedIn);
      setIsAdmin(loggedIn.isAdmin);
    } catch {
      console.log("User is not logged in");
      setIsAdmin(false);
    }
  };

  useEffect(() => {
    fetchWhoIsLoggedIn();
  }, []);

  useEffect(() => {
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
  }, [knownStorages]);

  return (
    <div className={classes.common_box_size}>
      <Typography variant="h3">Project Configuration</Typography>
      <Grid container direction="column">
        <Paper className={classes.paperWithPadding}>
          <Grid container spacing={4} alignItems="center">
            <Grid item xs={6} sm={6}>
              <TextField
                style={{ width: 300 }}
                label="Project Title"
                placeholder="My project title"
                helperText="Enter a good descriptive project name"
                margin="normal"
                id="projectNameInput"
                onChange={(event) =>
                  props.projectNameDidChange(event.target.value)
                }
                value={props.projectName}
              />
            </Grid>
            {isAdmin && (
              <>
                <Grid
                  container
                  item
                  xs={6}
                  sm={6}
                  alignItems="center"
                  justifyContent="flex-end"
                >
                  <Grid item xs={2}>
                    <Switch
                      id="autoNameCheck"
                      checked={autoName}
                      onChange={(event) => setAutoName(event.target.checked)}
                    />
                  </Grid>
                  <Grid item xs={8}>
                    <TextField
                      style={{ width: "100%" }}
                      id="fileNameInput"
                      onChange={(event) =>
                        props.fileNameDidChange(event.target.value)
                      }
                      value={props.fileName}
                      disabled={autoName}
                    />
                  </Grid>
                </Grid>
              </>
            )}
          </Grid>
        </Paper>

        <Paper className={classes.paperWithPadding}>
          <Grid item xs={12}>
            <TemplateComponent
              templateValue={props.templateValue}
              templateValueDidChange={props.templateValueDidChange}
            />
          </Grid>
        </Paper>
        <Paper className={classes.paperWithPadding}>
          <Grid item xs={12}>
            <ObituaryComponent
              valueDidChange={props.valueDidChange}
              value={props.value}
              isObituary={props.isObituary}
              checkBoxDidChange={props.checkBoxDidChange}
            />
          </Grid>
        </Paper>

        <Paper className={classes.paperWithPadding}>
          <Grid item xs={12}>
            <ProductionOfficeComponent
              productionOfficeValue={props.productionOfficeValue}
              valueWasSet={props.valueWasSet}
            />
          </Grid>
        </Paper>
      </Grid>
    </div>
  );
};

export default ConfigurationComponent;
