import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
import { Paper, Grid } from "@material-ui/core";
import { useGuardianStyles } from "~/misc/utils";
import { getItemsNotDeleted, getDeleteJob } from "../ProjectEntryList/helpers";
import { Helmet } from "react-helmet";
import { isLoggedIn } from "~/utils/api";
import { getDeletionRecord } from "./helpers";
import moment from "moment";

interface DeletionRecordStateTypes {
  id?: string;
}

type DeletionRecordProps = RouteComponentProps<DeletionRecordStateTypes>;

const DeletionRecord: React.FC<DeletionRecordProps> = (props) => {
  const classes = useGuardianStyles();

  const [deleteJobStatus, setDeleteJobStatus] = useState<string>("");
  const [itemsNotDeleted, setItemsNotDeleted] = useState<ItemsNotDeleted[]>([]);
  const [refreshInterval, setRefreshInterval] = useState<any>();
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [deletionRecord, setDeletionRecord] = useState<DeletionRecord>();

  const getDeleteItemData = async () => {
    try {
      const id = Number(deletionRecord?.projectEntry);
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

    const getDeletionRecordData = async () => {
      try {
        const id = Number(props.match.params.id);
        const returnedRecord = await getDeletionRecord(id);
        setDeletionRecord(returnedRecord);
      } catch {
        console.log("Could not load deletion record.");
      }
    };

    getDeletionRecordData();

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    if (deletionRecord != undefined) {
      const getDeleteJobData = async () => {
        try {
          const id = Number(deletionRecord?.projectEntry);
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
    }
  }, [deletionRecord]);

  useEffect(() => {
    if (deleteJobStatus == "Finished") {
      getDeleteItemData();
      clearInterval(refreshInterval);
    }
  }, [deleteJobStatus]);

  return (
    <>
      <Helmet>
        <title>Deletion Record {props.match.params.id} - Pluto Admin</title>
      </Helmet>
      {isAdmin ? (
        <div>
          <Grid container xs={12} direction="row" spacing={3}>
            <Grid item xs={12} style={{ fontSize: "2em" }}>
              Project Deletion Record {props.match.params.id}
            </Grid>
            <Grid item xs={12} style={{ fontSize: "1em" }}>
              Project: {deletionRecord?.projectEntry}
              <br />
              Owner: {deletionRecord?.user}
              <br />
              Deleted:{" "}
              {moment(deletionRecord?.deleted).format("DD/MM/YYYY HH:mm")}
              <br />
              Created:{" "}
              {moment(deletionRecord?.created).format("DD/MM/YYYY HH:mm")}
              <br />
              Working group: {deletionRecord?.workingGroup}
            </Grid>
          </Grid>
          {deleteJobStatus != "" ? (
            <Paper
              className={classes.root}
              elevation={3}
              style={{ marginTop: "30px" }}
            >
              <Grid container xs={12} direction="row" spacing={3}>
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
        </div>
      ) : (
        <div>You do not have access to this page.</div>
      )}
    </>
  );
};

export default DeletionRecord;
