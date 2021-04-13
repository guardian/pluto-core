import React, { useEffect, useState } from "react";
import {
  Paper,
  Button,
  Typography,
  makeStyles,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableBody,
  TableCell,
} from "@material-ui/core";
import ErrorOutlineIcon from "@material-ui/icons/ErrorOutline";
import WarningIcon from "@material-ui/icons/Warning";
import SystemNotification, {
  SystemNotificationKind,
} from "../SystemNotification";
import { authenticatedFetch } from "./auth";

const useStyles = makeStyles({
  projectDeliverable: {
    display: "flex",
    flexDirection: "column",
    padding: "1rem",
    marginTop: "1rem",

    "& .MuiTypography-subtitle1": {
      marginTop: "6px",
      marginBottom: "6px",
    },
    "& .error": {
      backgroundColor: "rgb(211 47 47)",
      padding: "10px",
      color: "#FFF",
      "& .content": {
        display: "flex",
        alignItems: "center",

        "& .message": {
          marginLeft: "6px",
        },
      },
    },
    "& .button-container": {
      marginTop: "1rem",
    },
  },
  loading: {
    display: "flex",
    flexDirection: "column",
    width: "100%",
    alignItems: "center",
  },
});

declare var vaultdoorURL: string;

const tableHeaderTitles: string[] = ["Filename", "Size", "Status"];

interface ProjectEntryVaultComponentProps {
  project: Project;
}

const ProjectEntryVaultComponent: React.FC<ProjectEntryVaultComponentProps> = (
  props
) => {
  const classes = useStyles();
  const [deliverable, setDeliverables] = useState<Deliverable[]>([]);
  const [
    deliverableCount,
    setDeliverableCount,
  ] = useState<DeliverablesCount | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [failed, setFailed] = useState<string>("");
  const { project } = props;
  const [knownVaults, setKnownVaults] = useState<Array<VaultDescription>>([]);

  const refresh = async () => {
    const response = await authenticatedFetch(`${vaultdoorURL}api/vault`, {});
    switch (response.status) {
      case 200:
        const content = (await response.json()) as Array<VaultDescription>;
        if (Array.isArray(content)) {
          const reversed = content.reverse();
          //this.setState({loading: false, lastError: null, knownVaults: reversed});
          setLoading(false);
          //setLastError(undefined);
          setKnownVaults(reversed);
        } else {
          console.error(
            "Expected server response to be an array, got ",
            content
          );
          //setLastError("Could not understand server response");
          setLoading(false);
        }
        break;
      default:
        const errorContent = await response.text();
        console.error(errorContent);
        setLoading(false);
        //setLastError(`Server error ${response.status}`);
        break;
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  const fetchVaultData = async (vaultId: string) => {
    const response = await authenticatedFetch(
      `${vaultdoorURL}api/vault/${vaultId}/projectSummary/${project.id}`,
      {}
    );
    switch (response.status) {
      case 200:
        const bodyText = await response.text();
        const content = JSON.parse(bodyText);
        return [content.total.count, content.total.size];
        break;
      default:
        const errorContent = await response.text();
        console.error(errorContent);
        return [0, 0];
        break;
    }
  };

  if (loading) {
    return (
      <div className={classes.loading}>
        <Typography variant="h4">Loading...</Typography>
      </div>
    );
  }

  return (
    <Paper className={classes.projectDeliverable}>
      <Typography variant="h4">Vaultdoor {vaultdoorURL}</Typography>
      <Table>
        <TableBody>
          {knownVaults.map(function (entry, idx) {
            console.log(fetchVaultData(entry.vaultId));

            return (
              <TableRow key={idx}>
                <TableCell>{entry.name}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </Paper>
  );
};

export default ProjectEntryVaultComponent;
