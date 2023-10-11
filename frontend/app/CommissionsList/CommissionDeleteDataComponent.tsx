import React, { useEffect, useState } from "react";
import { RouteComponentProps, useHistory, useLocation } from "react-router-dom";
import { Button, Checkbox, Grid, Paper } from "@material-ui/core";
import { getBuckets, getDeleteJob } from "../ProjectEntryList/helpers";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import { Helmet } from "react-helmet";
import { useGuardianStyles } from "~/misc/utils";
import { isLoggedIn } from "~/utils/api";
import {
  loadCommissionData,
  startDelete,
  projectsForCommission,
} from "./helpers";
import NotDeleted from "~/CommissionsList/NotDeleted";

declare var deploymentRootPath: string;

interface CommissionDeleteDataComponentStateTypes {
  commissionId?: string;
}

type CommissionDeleteDataComponentProps = RouteComponentProps<
  CommissionDeleteDataComponentStateTypes
>;

const CommissionDeleteDataComponent: React.FC<CommissionDeleteDataComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();
  const history = useHistory();

  const { state: projectFromList } = useLocation<Project | undefined>();
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [commission, setCommission] = useState<boolean>(false);
  const [pluto, setPluto] = useState<boolean>(false);
  const [file, setFile] = useState<boolean>(true);
  const [backups, setBackups] = useState<boolean>(true);
  const [pTR, setPTR] = useState<boolean>(true);
  const [deliverables, setDeliverables] = useState<boolean>(true);
  const [sAN, setSAN] = useState<boolean>(true);
  const [matrix, setMatrix] = useState<boolean>(true);
  const [s3, setS3] = useState<boolean>(true);
  const [buckets, setBuckets] = useState<string[]>([]);
  const [bucketBooleans, updateBucketBooleans] = useState<boolean[]>([]);
  const [commissionData, setCommissionData] = useState<
    CommissionFullRecord | undefined
  >(undefined);
  const [lastError, setLastError] = useState<null | string>(null);
  const [projectList, setProjectList] = useState<Project[] | undefined>(
    undefined
  );
  const [filterTerms, setFilterTerms] = useState<ProjectFilterTerms>({
    match: "W_STARTSWITH",
  });
  const [deleteJobStatuses, setDeleteJobStatuses] = useState<any[]>([]);

  useEffect(() => {
    if (projectFromList) {
      return;
    }

    let isMounted = true;

    const doLoadIn = () => {
      loadCommissionData(Number(props.match.params.commissionId))
        .then((comm) => {
          setCommissionData(comm);
        })
        .catch((err) => {
          console.error("Could not load commission: ", err);
          if (err.hasOwnProperty("response")) {
            console.log("Error was from a response, ", err.response);
            switch (err.response.status) {
              case 404:
                setLastError("This commission does not exist");
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

    let newFilters: ProjectFilterTerms = {
      match: "W_STARTSWITH",
    };
    newFilters.commissionId = Number(props.match.params.commissionId);
    setFilterTerms(newFilters);

    const doLoadProjects = () => {
      projectsForCommission(
        Number(props.match.params.commissionId),
        0,
        1000,
        filterTerms
      )
        .then(([projects, count]) => {
          setProjectList(projects);
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

    doLoadProjects();

    const fetchWhoIsLoggedIn = async () => {
      try {
        const loggedIn = await isLoggedIn();
        setIsAdmin(loggedIn.isAdmin);
      } catch {
        setIsAdmin(false);
      }
    };

    fetchWhoIsLoggedIn();

    const getBucketData = async () => {
      try {
        const returnedBuckets = await getBuckets();
        for (const bucket in returnedBuckets) {
          updateBucketBooleans((arr) => [...arr, true]);
        }
        setBuckets(returnedBuckets);
      } catch {
        console.log("Could not load buckets.");
      }
    };

    getBucketData();

    return () => {
      isMounted = false;
    };
  }, []);

  const onProjectSubmit = async (
    event: React.FormEvent<HTMLFormElement>
  ): Promise<void> => {
    event.preventDefault();

    if (commissionData) {
      try {
        await startDelete(
          commissionData.id,
          commission,
          pluto,
          file,
          backups,
          pTR,
          deliverables,
          sAN,
          matrix,
          s3,
          buckets,
          bucketBooleans
        );

        SystemNotification.open(
          SystemNotifcationKind.Success,
          `Successfully requested to delete data for commission "${commissionData.title}"`
        );
      } catch {
        SystemNotification.open(
          SystemNotifcationKind.Error,
          `Failed to delete data for commission "${commissionData.title}"`
        );
      }
    }
  };

  useEffect(() => {
    const getDeleteJobData = async () => {
      let deleteJobsArrayTwo: any[][] = [];

      if (projectList) {
        const goGetTheData = async () => {
          let statusString = "Unknown";
          const getJobData = async (projectObject: Project) => {
            try {
              statusString = await getDeleteJob(projectObject.id);
            } catch {
              console.log("Could not load delete job status.");
            }
            deleteJobsArrayTwo.push([projectObject.id, statusString]);
          };
          for (let projectObject of projectList) {
            await getJobData(projectObject);
          }
          return;
        };

        await goGetTheData();
      }

      const sortedArray = deleteJobsArrayTwo.sort(function (a, b) {
        return Number(a[0]) - Number(b[0]);
      });

      setDeleteJobStatuses(sortedArray);
    };

    if (projectList) {
      getDeleteJobData();

      const interval = setInterval(() => getDeleteJobData(), 20000);
    }
  }, [projectList]);

  return (
    <>
      {commissionData ? (
        <Helmet>
          <title>Delete Data for the Commission {commissionData.title}</title>
        </Helmet>
      ) : null}
      {isAdmin ? (
        <>
          {commissionData ? (
            <>
              <Grid container justifyContent="space-between" spacing={3}>
                <Grid item>
                  <h4>Delete Data for the Commission {commissionData.title}</h4>
                </Grid>
              </Grid>
              <Paper className={classes.root} elevation={3}>
                <form onSubmit={onProjectSubmit}>
                  <Grid container direction="row" spacing={3}>
                    <Grid item>
                      Commission Record
                      <Checkbox
                        checked={commission}
                        onChange={() => {
                          if (!commission) {
                            setPluto(true);
                          }
                          setCommission(!commission);
                        }}
                        name="commission"
                      />
                    </Grid>
                    <Grid item>
                      Project Records
                      <Checkbox
                        checked={pluto}
                        onChange={() => setPluto(!pluto)}
                        name="pluto"
                      />
                    </Grid>
                    <Grid item>
                      Project Files
                      <Checkbox
                        checked={file}
                        onChange={() => setFile(!file)}
                        name="file"
                      />
                    </Grid>
                    <Grid item>
                      Project File Backups
                      <Checkbox
                        checked={backups}
                        onChange={() => setBackups(!backups)}
                        name="backups"
                      />
                    </Grid>
                    <Grid item>
                      Pointer Files
                      <Checkbox
                        checked={pTR}
                        onChange={() => setPTR(!pTR)}
                        name="ptr"
                      />
                    </Grid>
                    <Grid item>
                      Deliverables
                      <Checkbox
                        checked={deliverables}
                        onChange={() => setDeliverables(!deliverables)}
                        name="deliverables"
                      />
                    </Grid>
                    <Grid item>
                      Storage Area Network Data and Vidispine Items
                      <Checkbox
                        checked={sAN}
                        onChange={() => setSAN(!sAN)}
                        name="san"
                      />
                    </Grid>
                    <Grid item>
                      Object Matrix Data
                      <Checkbox
                        checked={matrix}
                        onChange={() => setMatrix(!matrix)}
                        name="matrix"
                      />
                    </Grid>
                    <Grid item>
                      Amazon Web Services Simple Storage Service Data
                      <Checkbox
                        checked={s3}
                        onChange={() => setS3(!s3)}
                        name="s3"
                      />
                      {s3 ? (
                        <>
                          <br />
                          Buckets
                          <br />
                          {buckets
                            ? buckets.map((bucket, ix) => {
                                return (
                                  <div key={ix}>
                                    {bucket}
                                    <Checkbox
                                      checked={bucketBooleans[ix]}
                                      onChange={() => {
                                        let booleansCopy = [...bucketBooleans];
                                        booleansCopy[ix] = !bucketBooleans[ix];
                                        updateBucketBooleans(booleansCopy);
                                      }}
                                      name={bucket}
                                    />
                                    <br />
                                  </div>
                                );
                              })
                            : null}
                        </>
                      ) : null}
                    </Grid>
                  </Grid>
                  <div>
                    Please note the following: -
                    <br />
                    <br />
                    1. If you have 'Project Records' enabled it will break
                    deletion from Vidispine, the Storage Area Network, and the
                    Object Matrix system.
                    <br />
                    <br />
                    2. Some parts of the Pluto system where not designed to be
                    tolerant of data removal. Certain undesirable consequences
                    may occur if you remove data from the system.
                    <br />
                    <br />
                    3. Deletion from the Amazon Web Services Simple Storage
                    Service does not take into account that projects in other
                    commissions may reference items from projects in this
                    commission. If this is used to delete items which are
                    referenced by other projects, the other projects will not be
                    able to load the items.
                    <br />
                    <br />
                    4. Due to a limitation of the pluto-storagetier software,
                    which this software relies on, this software will only
                    attempt to delete from the one Object Matrix vault that
                    pluto-storagetier is configured to access.
                    <br />
                    <br />
                    5. Deletion from the Object Matrix system does not take into
                    account that projects in other commissions may reference
                    items from projects in this commission. If this is used to
                    delete items which are referenced by other projects, the
                    other projects will not be able to load the items.
                    <br />
                    <br />
                    6. Deletion from Vidispine does not take into account that
                    other commissions which are currently archived may reference
                    files from projects in this commission. If this is used to
                    delete files which are referenced by other projects, the
                    other projects will not be able to load the files.
                    <br />
                    <br />
                    7. This software will not delete the database backups for
                    Vidispine, pluto-core, and pluto-deliverables. Data such as
                    titles, owners, and file names from this commission will
                    remain present in these backups.
                    <br />
                    <br />
                    8. Any attempt to delete the commission record will fail
                    unless the project records are also set to be deleted.
                  </div>
                  <div className={classes.formButtons}>
                    <Button
                      className="cancel"
                      variant="outlined"
                      onClick={() => history.goBack()}
                    >
                      Back
                    </Button>
                    <Button type="submit" variant="contained" color="secondary">
                      Submit Delete Request
                    </Button>
                  </div>
                </form>
              </Paper>
              {deleteJobStatuses != []
                ? deleteJobStatuses.map((statusArray, ix) => {
                    return (
                      <div key={ix}>
                        {statusArray[1] != "Unknown" ? (
                          <>
                            {ix == 0 ? (
                              <Grid
                                container
                                justifyContent="space-between"
                                spacing={3}
                              >
                                <Grid item>
                                  <h4>
                                    Storage Area Network Data Delete Job
                                    Outcomes
                                  </h4>
                                </Grid>
                              </Grid>
                            ) : null}
                            <Paper
                              className={classes.root}
                              elevation={3}
                              style={{ marginBottom: "40px" }}
                            >
                              <Grid container direction="row" spacing={3}>
                                <Grid
                                  item
                                  xs={12}
                                  style={{ fontSize: "1.6em" }}
                                >
                                  Project {statusArray[0]}
                                </Grid>
                                <Grid item xs={12}>
                                  {statusArray[1] == "Started" ? (
                                    <>Job running...</>
                                  ) : null}
                                  {statusArray[1] == "Finished" ? (
                                    <>
                                      Deletion instructions sent to RabbitMQ.
                                      <NotDeleted
                                        projectId={statusArray[0]}
                                      ></NotDeleted>
                                    </>
                                  ) : null}
                                </Grid>
                              </Grid>
                            </Paper>
                          </>
                        ) : null}
                      </div>
                    );
                  })
                : null}
            </>
          ) : (
            <div>
              No commission data for commission{" "}
              {props.match.params.commissionId} is present in the system. It may
              have been deleted.
            </div>
          )}
        </>
      ) : (
        <div>You do not have access to this page.</div>
      )}
    </>
  );
};

export default CommissionDeleteDataComponent;
