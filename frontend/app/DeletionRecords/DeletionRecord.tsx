import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
import { Paper, Grid } from "@material-ui/core";
import { useGuardianStyles } from "~/misc/utils";
import { getItemsNotDeleted, getDeleteJob } from "../ProjectEntryList/helpers";
import { Helmet } from "react-helmet";
import { isLoggedIn } from "~/utils/api";

interface DeletionRecordStateTypes {
  projectEntry?: string;
}

type DeletionRecordProps = RouteComponentProps<DeletionRecordStateTypes>;

const DeletionRecord: React.FC<DeletionRecordProps> = (props) => {
  const classes = useGuardianStyles();

  const [deleteJobStatus, setDeleteJobStatus] = useState<string>("");
  const [itemsNotDeleted, setItemsNotDeleted] = useState<ItemsNotDeleted[]>([]);
  const [refreshInterval, setRefreshInterval] = useState<any>();
  const [isAdmin, setIsAdmin] = useState<boolean>(false);

  const getDeleteItemData = async () => {
    try {
      const id = Number(props.match.params.projectEntry);
      const returnedItems = await getItemsNotDeleted(id);
      setItemsNotDeleted(returnedItems);
    } catch {
      console.log("Could not load items that where not deleted.");
    }
  };

  useEffect(() => {
    let isMounted = true;

    const fetchWhoIsLoggedIn = async () => {
      try {
        const loggedIn = await isLoggedIn();
        setIsAdmin(loggedIn.isAdmin);
      } catch {
        setIsAdmin(false);
      }
    };

    fetchWhoIsLoggedIn();

    const getDeleteJobData = async () => {
      try {
        const id = Number(props.match.params.projectEntry);
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

  useEffect(() => {
    if (deleteJobStatus == "Finished") {
      getDeleteItemData();
      clearInterval(refreshInterval);
    }
  }, [deleteJobStatus]);

  return (
    <>
      <Helmet>
        <title>
          Deletion Record for Project {props.match.params.projectEntry} - Pluto
          Admin
        </title>
      </Helmet>
      {isAdmin ? (
        <Paper className={classes.root} elevation={3}>
          <Grid container xs={12} direction="row" spacing={3}>
            <Grid item xs={12} style={{ fontSize: "2em" }}>
              Project {props.match.params.projectEntry} Deletion Record
            </Grid>
            <Grid item xs={12} style={{ fontSize: "1.6em" }}>
              Storage Area Network Data Outcome
            </Grid>
            <Grid item xs={12}>
              {deleteJobStatus == "Started" ? <>Job running...</> : null}
              {deleteJobStatus == "Finished" ? (
                <>
                  Deletion instructions sent to RabbitMQ.
                  {itemsNotDeleted.length > 0 ? (
                    <>
                      <br />
                      <br />
                      No attempt to delete the following items was made due to
                      them being in more than one project:-
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
      ) : (
        <div>You do not have access to this page.</div>
      )}
    </>
  );
};

export default DeletionRecord;
