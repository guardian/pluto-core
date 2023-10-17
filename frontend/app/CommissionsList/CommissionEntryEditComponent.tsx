import React, { useState, useEffect } from "react";
import { RouteComponentProps, useHistory } from "react-router";
import { Breadcrumb } from "@guardian/pluto-headers";
import {
  Button,
  CircularProgress,
  FormControl,
  FormLabel,
  Grid,
  IconButton,
  Paper,
  Select,
  TextField,
  Typography,
  Tooltip,
  Dialog,
  DialogContent,
  DialogContentText,
  DialogActions,
  Input,
  InputLabel,
} from "@material-ui/core";
import {
  KeyboardDatePicker,
  MuiPickersUtilsProvider,
} from "@material-ui/pickers";
import DateFnsUtils from "@date-io/date-fns";
import ParseDateISO from "date-fns/parseISO";
import FormatDateISO from "date-fns/formatISO";
import FormatDate from "date-fns/format";
import {
  loadCommissionData,
  projectsForCommission,
  updateCommissionData,
} from "./helpers";
import ErrorIcon from "@material-ui/icons/Error";
import LaunchIcon from "@material-ui/icons/Launch";
import WorkingGroupEntryView from "../EntryViews/WorkingGroupEntryView";
import ProductionOfficeSelector from "../common/ProductionOfficeSelector";
import WorkingGroupSelector from "../common/WorkingGroupSelector";
import StatusSelector from "../common/StatusSelector";
import ProjectsTable from "../ProjectEntryList/ProjectsTable";
import { Helmet } from "react-helmet";
import HelpIcon from "@material-ui/icons/Help";
import CommissionEntryDeliverablesComponent from "./CommissionEntryDeliverablesComponent";
import ChipsWithWarning from "./ChipsWithWarning";
import UsersAutoComplete from "../common/UsersAutoComplete";
import { useGuardianStyles } from "~/misc/utils";
import ProjectFilterComponent from "~/filter/ProjectFilterComponent";
import { filterTermsToQuerystring } from "~/filter/terms";
import { isLoggedIn } from "~/utils/api";
declare var deploymentRootPath: string;

interface CommissionEntryFormProps {
  commission: CommissionFullRecord;
  workingGroupName: string;
  isSaving: boolean;
  onSubmit: (evt: React.FormEvent<HTMLFormElement>) => void;
  onChange: (newValue: CommissionFullRecord) => void;
}

const CommissionEntryForm: React.FC<CommissionEntryFormProps> = (props) => {
  const history = useHistory();
  const classes = useGuardianStyles();

  const fieldValueChanged = (
    value: string | null,
    field: keyof CommissionFullRecord
  ): void => {
    props.onChange({ ...props.commission, [field]: value });
  };

  const fieldChanged = (
    event: React.ChangeEvent<
      | HTMLTextAreaElement
      | HTMLInputElement
      | HTMLSelectElement
      | { name?: string; value: string }
    >,
    field: keyof CommissionFullRecord
  ): void => {
    fieldValueChanged(event.target.value, field);
  };

  let createdTime: string | undefined = undefined;
  let timeValue;
  try {
    timeValue = ParseDateISO(props.commission.created);
    createdTime = FormatDate(timeValue, "E do MMM yyyy, h:mm a");
  } catch (err) {
    console.warn("Invalid creation time ", props.commission.created, ": ", err);
    console.warn("timeValue was ", timeValue);
  }

  return (
    <form onSubmit={props.onSubmit} className={classes.root}>
      <Grid container xs={12} direction="row" spacing={3}>
        {/*left-hand column*/}
        <Grid item xs={6}>
          <TextField
            id="title"
            label="Title"
            value={props.commission.title}
            onChange={(evt) => fieldChanged(evt, "title")}
          />

          <WorkingGroupSelector
            workingGroupId={props.commission.workingGroupId}
            onChange={(evt) => fieldChanged(evt, "workingGroupId")}
          />
          <TextField
            id="original-commissioner"
            label="Originally commissioned by"
            value={props.commission.originalCommissionerName}
            disabled={true}
          />

          <TextField
            id="description"
            label="Description/Brief"
            multiline={true}
            value={props.commission.description ?? ""}
            onChange={(evt) => fieldChanged(evt, "description")}
          />

          <FormControl>
            <FormLabel htmlFor="scheduled-completion">
              Scheduled completion
            </FormLabel>
            <MuiPickersUtilsProvider utils={DateFnsUtils}>
              <KeyboardDatePicker
                variant="inline"
                format="yyyy-MM-dd"
                margin="normal"
                id="scheduled-completion"
                value={props.commission.scheduledCompletion}
                onChange={(newValue: Date | null) => {
                  if (newValue)
                    props.onChange({
                      ...props.commission,
                      scheduledCompletion: FormatDateISO(newValue),
                    });
                }}
              />
            </MuiPickersUtilsProvider>
          </FormControl>
        </Grid>
        {/*right-hand column*/}
        <Grid item xs={6}>
          <UsersAutoComplete
            valueDidChange={(evt, newValue) =>
              fieldValueChanged(newValue?.join("|") ?? "", "owner")
            }
            label="Owner"
            value={props.commission.owner}
            shouldValidate={true}
          />
          <TextField
            id="created"
            label="Created"
            value={createdTime ?? "(error)"}
            disabled={true}
          />
          <StatusSelector
            value={props.commission.status}
            onChange={(evt: any) => fieldChanged(evt, "status")}
          />
          <ProductionOfficeSelector
            label="Production Office"
            value={props.commission.productionOffice}
            onChange={(evt: any) => fieldChanged(evt, "productionOffice")}
          />
          <TextField
            id="notes"
            label="Notes"
            value={props.commission.notes ?? ""}
            multiline={true}
            onChange={(evt) => fieldChanged(evt, "notes")}
          />
          {props.commission.googleFolder ? (
            <div>
              View Documents
              <br />
              {props.commission.googleFolder ? (
                <Tooltip title="Open commission folder in Google Drive" arrow>
                  <a href={props.commission.googleFolder} target="_blank">
                    <img
                      className="smallicon"
                      src="/pluto-core/assets/images/google-drive-folder-icon.png"
                    />
                  </a>
                </Tooltip>
              ) : (
                <div>
                  <div className={classes.noGoogleText}>None</div>
                  <Tooltip
                    title="We have not implemented the functionality to create Google Drive folders due to some limitations and technical issues. If you would like the functionality please request it from multimediatech@theguardian.com"
                    arrow
                  >
                    <HelpIcon className={classes.warningIcon} />
                  </Tooltip>
                </div>
              )}
            </div>
          ) : null}

          <div className={classes.formButtons}>
            {props.isSaving ? (
              <CircularProgress style={{ width: "18px", height: "18px" }} />
            ) : null}
            <Button
              className="cancel"
              variant="outlined"
              onClick={() => history.goBack()}
              disabled={props.isSaving}
            >
              Back
            </Button>
            <Button type="submit" variant="outlined" disabled={props.isSaving}>
              Update
            </Button>
          </div>
        </Grid>
      </Grid>
    </form>
  );
};

interface RouteComponentMatches {
  commissionId: string;
}

const CommissionEntryEditComponent: React.FC<RouteComponentProps<
  RouteComponentMatches
>> = (props) => {
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [commissionData, setCommissionData] = useState<
    CommissionFullRecord | undefined
  >(undefined);
  const [projectList, setProjectList] = useState<Project[] | undefined>(
    undefined
  );
  const [lastError, setLastError] = useState<null | string>(null);
  const [isSaving, setIsSaving] = useState<boolean>(false);

  const classes = useGuardianStyles();
  const history = useHistory();
  const [errorDialog, setErrorDialog] = useState<boolean>(false);
  const [filterTerms, setFilterTerms] = useState<ProjectFilterTerms>({
    commissionId: parseInt(props.match.params.commissionId),
    match: "W_STARTSWITH",
  });
  const [user, setUser] = useState<PlutoUser | null>(null);
  const [projectCount, setProjectCount] = useState<number>(0);
  const [deliverablesSearchString, setDeliverablesSearchString] = useState<
    string
  >("");

  let commissionId: number;
  try {
    commissionId = parseInt(props.match.params.commissionId);
  } catch (err) {
    console.error(
      "Could not convert ",
      props.match.params.commissionId,
      " into a number"
    );
    commissionId = -1;
  }

  /**
   * load in member projects on launch
   */
  useEffect(() => {
    const doLoadIn = () => {
      projectsForCommission(commissionId, 0, 5, filterTerms)
        .then(([projects, count]) => {
          setProjectList(projects);
          setProjectCount(count);
          setLastError(null);
        })
        .catch((err) => {
          if (err.hasOwnProperty("response")) {
            console.error(
              "Server returned an error loading projects list: ",
              err.response
            );
            switch (err.response.status) {
              case 400:
                setLastError(
                  "Could not load projects, client-side search error"
                );
                break;
              case 500:
                console.log("Server said", err.response.body);
                setLastError(
                  "Could not load projects, server error, see console"
                );
                break;
              case 503:
              case 504:
                setLastError("Server not responding, retrying...");
                window.setTimeout(doLoadIn, 1000);
                break;
            }
          } else {
            console.error("Browser error trying to load projects list: ", err);
            setLastError("Browser error loading projects list");
          }
        });
    };

    doLoadIn();
  }, [filterTerms]);

  /**
   * load in commission data on launch
   */
  useEffect(() => {
    const doLoadIn = () => {
      loadCommissionData(commissionId)
        .then((comm) => {
          setCommissionData(comm);
          setIsLoading(false);
        })
        .catch((err) => {
          console.error("Could not load commission: ", err);
          setIsLoading(false);
          if (err.hasOwnProperty("response")) {
            console.log("Error was from a response, ", err.response);
            switch (err.response.status) {
              case 404:
                setLastError("This commission does not exist");
                setErrorDialog(true);
                break;
              case 500:
                setLastError(`Server error: ${err.response.body}`);
                break;
              case 503:
              case 504:
                setLastError("Server is not responding");
                window.setTimeout(doLoadIn, 1000); //try again in 1s
                break;
              default:
                setLastError(`Server returned ${err.response.status}`);
                break;
            }
          } else {
            console.error("Could not load in commission data: ", err);
            setLastError(err.toString());
          }
        });
    };
    doLoadIn();
  }, []);

  /**
   * if the commission data changes, then load in the contents
   */
  useEffect(() => {}, [commissionData]);

  const closeDialog = () => {
    setErrorDialog(false);
    props.history.goBack();
  };

  useEffect(() => {
    const fetchWhoIsLoggedIn = async () => {
      try {
        let user = await isLoggedIn();
        user.uid = generateUserName(user.uid);
        setUser(user);
      } catch (error) {
        console.error("Could not login user:", error);
      }
    };

    fetchWhoIsLoggedIn();
  }, []);

  const stringUpdated = (
    event: React.ChangeEvent<HTMLTextAreaElement | HTMLInputElement>
  ) => {
    const newValue = event.target.value;
    setDeliverablesSearchString(newValue);
  };

  const generateUserName = (inputString: string) => {
    if (inputString.includes("@")) {
      const splitString = inputString.split("@", 1)[0];
      const userNameConst = splitString.replace(".", "_");
      return userNameConst;
    }
    return inputString;
  };

  return (
    <>
      {commissionData ? (
        <Helmet>
          <title>[{commissionData.title}] Details</title>
        </Helmet>
      ) : null}

      <Breadcrumb
        commissionId={commissionId}
        plutoCoreBaseUri={
          deploymentRootPath.endsWith("/")
            ? deploymentRootPath.substr(0, deploymentRootPath.length - 1)
            : deploymentRootPath
        }
      />
      <Paper elevation={3}>
        {isLoading ? (
          <Grid
            container
            direction="row"
            justifyContent="space-around"
            alignContent="center"
          >
            <Grid item xs>
              <CircularProgress
                className={classes.inlineThrobber}
                color="secondary"
              />
              <Typography className={classes.inlineText}>Loading...</Typography>
            </Grid>
          </Grid>
        ) : null}
        {lastError ? (
          <Grid container direction="row" justifyContent="space-around">
            <Grid item xs className={classes.errorBlock}>
              <ErrorIcon className={classes.inlineThrobber} />
              <Typography className={classes.inlineText}>
                {lastError}
              </Typography>
            </Grid>
          </Grid>
        ) : null}
        {commissionData ? (
          <CommissionEntryForm
            commission={commissionData}
            workingGroupName="test"
            isSaving={isSaving}
            onSubmit={(evt) => {
              evt.preventDefault();
              setIsSaving(true);
              updateCommissionData(commissionData)
                .then(() => {
                  setIsSaving(false);
                  history.push("/commission");
                })
                .catch((err) => {
                  setIsSaving(false);
                  if (err.hasOwnProperty("response")) {
                    console.error(
                      "Server error saving record: ",
                      err.response.status
                    );
                    console.error("Server said: ", err.response.body);

                    switch (err.response.status) {
                      case 502:
                      case 503:
                        setLastError(
                          "Server is not responding. Try saving again in a minute"
                        );
                        break;
                      case 500:
                        setLastError("Server error, see logs for details");
                        break;
                      case 400:
                        setLastError("Some values are incorrect");
                        break;
                      default:
                        setLastError(
                          `Unexpected server response ${err.response.status}`
                        );
                        break;
                    }
                  } else {
                    setLastError("Browser error, see console for details.");
                    console.error("Could not make update request: ", err);
                  }
                });
            }}
            onChange={(newValue) => setCommissionData(newValue)}
          />
        ) : null}
      </Paper>
      {/*will repace this with an icon*/}
      <Grid container direction="row" justifyContent="space-between">
        <Grid item>
          <Typography variant="h4">Projects</Typography>
        </Grid>
        <Grid item>
          <Button
            variant="contained"
            color="primary"
            onClick={() =>
              history.push(
                `/project/new?commissionId=${commissionId}&workingGroupId=${commissionData?.workingGroupId}`
              )
            }
          >
            New Project
          </Button>
        </Grid>
      </Grid>
      <Grid container>
        {filterTerms ? (
          <Grid item>
            <ProjectFilterComponent
              filterTerms={filterTerms}
              filterDidUpdate={(newFilters: ProjectFilterTerms) => {
                console.log(
                  "ProjectFilterComponent filterDidUpdate ",
                  newFilters
                );
                const updatedUrlParams = filterTermsToQuerystring(newFilters);

                if (newFilters.user === "Everyone") {
                  newFilters.user = undefined;
                }

                if (newFilters.title) {
                  newFilters.match = "W_CONTAINS";
                }

                if (newFilters.user === "Mine" && user) {
                  newFilters.user = user.uid;
                }
                setFilterTerms(newFilters);

                history.push("?" + updatedUrlParams);
              }}
            />
          </Grid>
        ) : null}
      </Grid>
      <Paper elevation={3}>
        {projectList ? (
          <ProjectsTable
            className={classes.table}
            pageSizeOptions={[5, 10, 20]}
            updateRequired={(page, pageSize) => {
              projectsForCommission(commissionId, page, pageSize, filterTerms)
                .then(([projects, count]) => {
                  setProjectList(projects);
                  setProjectCount(count);
                })
                .catch((err) =>
                  console.error("Could not update project list: ", err)
                );
            }}
            projects={projectList}
            projectCount={projectCount}
          />
        ) : null}
      </Paper>
      <Grid
        container
        direction="row"
        justifyContent="space-between"
        style={{ marginTop: "20px", marginBottom: "10px" }}
      >
        <Grid item>
          <Typography variant="h4">Deliverables</Typography>
        </Grid>
        <Grid item>
          <FormControl>
            <InputLabel>Name Filter</InputLabel>
            <Input onChange={(event) => stringUpdated(event)} />
          </FormControl>
        </Grid>
      </Grid>
      <Paper elevation={3}>
        {commissionData ? (
          <CommissionEntryDeliverablesComponent
            commission={commissionData}
            searchString={deliverablesSearchString}
          />
        ) : null}
      </Paper>
      <Dialog
        open={errorDialog}
        onClose={closeDialog}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            The requested commission does not exist.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default CommissionEntryEditComponent;
