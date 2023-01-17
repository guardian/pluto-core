import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router";
import { Helmet } from "react-helmet";
import {
  CircularProgress,
  Grid,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  Tooltip,
} from "@material-ui/core";
import { Add } from "@material-ui/icons";
import { sortListByOrder } from "../utils/lists";
import VersionTranslationRow from "./VersionTranslationRow";
import axios from "axios";
import { SystemNotifcationKind, SystemNotification } from "@guardian/pluto-headers";
import { useGuardianStyles } from "~/misc/utils";

const VersionTranslationsList: React.FC<RouteComponentProps> = (props) => {
  const [knownTranslations, setKnownTranslations] = useState<
    PremiereVersionTranslation[]
  >([]);
  const [loading, setLoading] = useState(false);

  const [sortDescending, setSortDescending] = useState(true);

  const classes = useGuardianStyles();

  const addNew = () => {
    setKnownTranslations((prev) =>
      prev.concat({
        internalVersionNumber: 0,
        name: "",
        displayedVersion: "",
      })
    );
  };

  const deleteEntry = async (id: number) => {
    try {
      const response = await axios.delete(`/api/premiereVersion/${id}`);
      SystemNotification.open(
        SystemNotifcationKind.Success,
        `Deleted premeire version translation ${id}`
      );
      reloadData();
    } catch (err) {
      console.error(
        `Could not delete premiere version translation ${id}: `,
        err
      );
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Could not delete version translation - see browser console log`
      );
    }
  };

  const updateEntry = async (newValue: PremiereVersionTranslation) => {
    try {
      const response = await axios.post(`/api/premiereVersion`, newValue);
      SystemNotification.open(
        SystemNotifcationKind.Success,
        `Updated premiere version translation ${newValue.internalVersionNumber}`
      );
      reloadData();
    } catch (err) {
      console.error(
        `Could not update premiere version translation`,
        newValue,
        err
      );
      SystemNotification.open(
        SystemNotifcationKind.Error,
        "Could not update version translation - see browser console log"
      );
      return Promise.reject("server error");
    }
  };

  const reloadData = async () => {
    setLoading(true);
    try {
      const response = await axios.get<
        ObjectListResponse<PremiereVersionTranslation>
      >("/api/premiereVersion");
      setKnownTranslations(response.data.result);
      setLoading(false);
    } catch (err) {
      console.error("Could not load data: ", err);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        "Could not load version translations due to a server error"
      );
      setLoading(false);
    }
  };

  useEffect(() => {
    reloadData();
  }, []);

  return (
    <>
      <Helmet>
        <title>Premiere version translations - Pluto admin</title>
      </Helmet>
      <Grid
        container
        spacing={3}
        justifyContent="space-between"
        alignContent="flex-end"
        className={classes.iconBanner}
      >
        <Grid item>{loading ? <CircularProgress /> : undefined}</Grid>
        <Grid item>
          <Tooltip title="Add a new version translation">
            <IconButton onClick={addNew}>
              <Add />
            </IconButton>
          </Tooltip>
        </Grid>
      </Grid>
      <Paper elevation={3}>
        <TableContainer>
          <Table className={classes.table}>
            <TableHead>
              <TableRow>
                <TableCell sortDirection={sortDescending ? "desc" : "asc"}>
                  <TableSortLabel
                    direction={sortDescending ? "desc" : "asc"}
                    active={true}
                    onClick={() => setSortDescending((prev) => !prev)}
                  >
                    Internal version number
                  </TableSortLabel>
                </TableCell>
                <TableCell>App name</TableCell>
                <TableCell>Display version</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortListByOrder(
                knownTranslations,
                "internalVersionNumber",
                sortDescending ? "desc" : "asc"
              ).map((entry, idx) => (
                <VersionTranslationRow
                  entry={entry}
                  key={idx}
                  requestDelete={deleteEntry}
                  requestUpdate={updateEntry}
                />
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </>
  );
};

export default VersionTranslationsList;
