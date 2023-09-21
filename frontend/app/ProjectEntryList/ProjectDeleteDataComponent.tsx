import React, { useEffect, useState } from "react";
import { RouteComponentProps, useHistory, useLocation } from "react-router-dom";
import { Button, Checkbox, Grid, Paper } from "@material-ui/core";
import {
  getProject,
  getProjectByVsid,
  startDelete,
  getBuckets,
} from "./helpers";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import { Helmet } from "react-helmet";
import { useGuardianStyles } from "~/misc/utils";
import { isLoggedIn } from "~/utils/api";

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
  const [pluto, setPluto] = useState<boolean>(true);
  const [file, setFile] = useState<boolean>(true);
  const [backups, setBackups] = useState<boolean>(true);
  const [pTR, setPTR] = useState<boolean>(true);
  const [deliverables, setDeliverables] = useState<boolean>(true);
  const [sAN, setSAN] = useState<boolean>(false);
  const [matrix, setMatrix] = useState<boolean>(false);
  const [s3, setS3] = useState<boolean>(true);
  const [buckets, setBuckets] = useState<string[]>([]);
  const [bucketBooleans, updateBucketBooleans] = useState<boolean[]>([]);

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

    return () => {
      isMounted = false;
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
                {/*
                    <Grid item>
                      Storage Area Network Data
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
                    */}
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
                Please note: some parts of the Pluto system where not designed
                to be tolerant of data removal. Certain undesirable consequences
                may occur if you remove data from the system.
                <br />
                <br />
                Please note: deletion from the Amazon Web Services Simple
                Storage Service does not take into account that other projects
                may reference items from this project. If this is used to delete
                items which are referenced by other projects, the other projects
                will not be able to load the items.
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
        </>
      ) : (
        <div>You do not have access to this page.</div>
      )}
    </>
  );
};

export default ProjectDeleteDataComponent;
