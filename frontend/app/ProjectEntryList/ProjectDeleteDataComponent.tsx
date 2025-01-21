import React, { useEffect, useState } from "react";
import { RouteComponentProps, useHistory, useLocation } from "react-router-dom";
import {
  Button,
  Checkbox,
  Grid,
  IconButton,
  Paper,
  Tooltip,
} from "@material-ui/core";
import {
  getProject,
  getProjectByVsid,
  startDelete,
  getBuckets,
  getDeleteJob,
  getItemsNotDeleted,
} from "./helpers";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import { Helmet } from "react-helmet";
import { useGuardianStyles } from "~/misc/utils";
import { isLoggedIn } from "~/utils/api";
import { PermMedia } from "@material-ui/icons";

declare var deploymentRootPath: string;

interface ProjectDeleteDataComponentStateTypes {
  itemid?: string;
}

type ProjectDeleteDataComponentProps = RouteComponentProps<
  ProjectDeleteDataComponentStateTypes
>;

const EMPTY_PROJECT: Project = {
  commissionId: -1,
  created: new Date().toLocaleDateString(),
  deep_archive: false,
  deletable: false,
  id: 0,
  productionOffice: "UK",
  isObitProject: null,
  projectTypeId: 0,
  sensitive: false,
  status: "New",
  title: "",
  user: "",
  workingGroupId: 0,
  confidential: false,
};

const ProjectDeleteDataComponent: React.FC<ProjectDeleteDataComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();
  const history = useHistory();

  const { state: projectFromList } = useLocation<Project | undefined>();
  const [project, setProject] = useState<Project>(
    projectFromList ?? EMPTY_PROJECT
  );
  const [errorDialog, setErrorDialog] = useState<boolean>(false);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
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
  const [deleteJobStatus, setDeleteJobStatus] = useState<string>("");
  const [itemsNotDeleted, setItemsNotDeleted] = useState<ItemsNotDeleted[]>([]);
  const [refreshInterval, setRefreshInterval] = useState<any>();

  const getDeleteItemData = async () => {
    try {
      const id = Number(props.match.params.itemid);
      const returnedItems = await getItemsNotDeleted(id);
      setItemsNotDeleted(returnedItems);
    } catch {
      console.log("Could not load items that where not deleted.");
    }
  };

  useEffect(() => {
    if (projectFromList) {
      return;
    }

    let isMounted = true;

    if (props.match.params.itemid !== "new") {
      const loadProject = async (): Promise<void> => {
        if (!props.match.params.itemid) throw "No project ID to load";
        const id = Number(props.match.params.itemid);

        try {
          const project = isNaN(id)
            ? await getProjectByVsid(props.match.params.itemid)
            : await getProject(id);
          if (isMounted) {
            setProject(project);
          }
        } catch (error) {
          if (error.message == "Request failed with status code 404") {
            setErrorDialog(true);
          }
        }
      };

      loadProject();
    }

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

    const getDeleteJobData = async () => {
      try {
        const id = Number(props.match.params.itemid);
        const returnedStatus = await getDeleteJob(id);
        setDeleteJobStatus(returnedStatus);
      } catch {
        console.log("Could not load delete job status.");
      }
    };

    getDeleteJobData();

    const interval = setInterval(() => getDeleteJobData(), 10000);
    setRefreshInterval(interval);

    getDeleteItemData();

    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  const onProjectSubmit = async (
    event: React.FormEvent<HTMLFormElement>
  ): Promise<void> => {
    event.preventDefault();

    if (project.title) {
      try {
        await startDelete(
          project.id,
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
          `Successfully requested to delete data for project "${project.title}"`
        );
      } catch {
        SystemNotification.open(
          SystemNotifcationKind.Error,
          `Failed to delete data for project "${project.title}"`
        );
      }
    }
  };

  useEffect(() => {
    if (deleteJobStatus == "Finished") {
      getDeleteItemData();
      clearInterval(refreshInterval);
    }
  }, [deleteJobStatus]);

  return (
    <>
      {project ? (
        <Helmet>
          <title>Delete Data for the Project {project.title}</title>
        </Helmet>
      ) : null}
      {isAdmin ? (
        <>
          <Grid container justifyContent="space-between" spacing={3}>
            <Grid item>
              <h4>Delete Data for the Project {project.title}</h4>
            </Grid>
          </Grid>
          <Paper className={classes.root} elevation={3}>
            <form onSubmit={onProjectSubmit}>
              <Grid container xs={12} direction="row" spacing={3}>
                <Grid item>
                  Project Record
                  <Checkbox
                    checked={pluto}
                    onChange={() => setPluto(!pluto)}
                    name="pluto"
                  />
                </Grid>
                <Grid item>
                  Project File
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
                  Pointer File
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
                              <>
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
                              </>
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
                1. If you have 'Project Record' enabled it will break deletion
                from Vidispine, the Storage Area Network, and the Object Matrix
                system.
                <br />
                <br />
                2. Some parts of the Pluto system where not designed to be
                tolerant of data removal. Certain undesirable consequences may
                occur if you remove data from the system.
                <br />
                <br />
                3. Deletion from the Amazon Web Services Simple Storage Service
                does not take into account that other projects may reference
                items from this project. If this is used to delete items which
                are referenced by other projects, the other projects will not be
                able to load the items.
                <br />
                <br />
                4. Due to a limitation of the pluto-storagetier software, which
                this software relies on, this software will only attempt to
                delete from the one Object Matrix vault that pluto-storagetier
                is configured to access.
                <br />
                <br />
                5. Deletion from the Object Matrix system does not take into
                account that other projects may reference items from this
                project. If this is used to delete items which are referenced by
                other projects, the other projects will not be able to load the
                items.
                <br />
                <br />
                6. Deletion from Vidispine does not take into account that other
                projects which are currently archived may reference files from
                this project. If this is used to delete files which are
                referenced by other projects, the other projects will not be
                able to load the files.
                <br />
                <br />
                7. This software will not delete the database backups for
                Vidispine, pluto-core, and pluto-deliverables. Data such as
                titles, owners, and file names from this project will remain
                present in these backups.
              </div>
              <div className={classes.formButtons}>
                <Button
                  className="cancel"
                  variant="outlined"
                  onClick={() => history.goBack()}
                >
                  Back
                </Button>
                <Tooltip title="See project's media">
                  <IconButton
                    onClick={() =>
                      window.location.assign(`/vs/project/${project.id}`)
                    }
                  >
                    <PermMedia />
                  </IconButton>
                </Tooltip>
                <Button type="submit" variant="contained" color="secondary">
                  Submit Delete Request
                </Button>
              </div>
            </form>
          </Paper>
          {deleteJobStatus != "" ? (
            <Paper
              className={classes.root}
              elevation={3}
              style={{ marginTop: "40px" }}
            >
              <Grid container xs={12} direction="row" spacing={3}>
                <Grid item xs={12} style={{ fontSize: "1.6em" }}>
                  Storage Area Network Data Delete Job Outcome
                </Grid>
                <Grid item xs={12}>
                  {deleteJobStatus == "Started" ? <>Job running...</> : null}
                  {deleteJobStatus == "Finished" ? (
                    <>
                      Deletion instructions sent to RabbitMQ. Please check there
                      for progress.
                      {itemsNotDeleted.length > 0 ? (
                        <>
                          <br />
                          <br />
                          No attempt to delete the following items was made due
                          to them being in more than one project:-
                          <br />
                        </>
                      ) : null}
                      {itemsNotDeleted
                        ? itemsNotDeleted.map((vidispine_item) => {
                            const { id, projectEntry, item } = vidispine_item;
                            return (
                              <>
                                <a href={"/vs/item/" + item} target="_blank">
                                  {item}
                                </a>
                                <br />
                              </>
                            );
                          })
                        : null}
                    </>
                  ) : null}
                </Grid>
              </Grid>
            </Paper>
          ) : null}
        </>
      ) : (
        <div>You do not have access to this page.</div>
      )}
    </>
  );
};

export default ProjectDeleteDataComponent;
