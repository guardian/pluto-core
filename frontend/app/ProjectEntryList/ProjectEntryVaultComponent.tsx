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
  projectVaultData: {
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

interface ProjectEntryVaultComponentProps {
  project: Project;
}

const ProjectEntryVaultComponent: React.FC<ProjectEntryVaultComponentProps> = (
  props
) => {
  const classes = useStyles();
  const [loading, setLoading] = useState<boolean>(true);
  const { project } = props;
  const [knownVaults, setKnownVaults] = useState<Array<VaultDescription>>([]);
  const [vaultCount0, setVaultCount0] = useState<number>(0);
  const [vaultCount1, setVaultCount1] = useState<number>(0);
  const [vaultCount2, setVaultCount2] = useState<number>(0);
  const [vaultCount3, setVaultCount3] = useState<number>(0);
  const [vaultCount4, setVaultCount4] = useState<number>(0);
  const [vaultCount5, setVaultCount5] = useState<number>(0);
  const [vaultCount6, setVaultCount6] = useState<number>(0);
  const [vaultCount7, setVaultCount7] = useState<number>(0);
  const [vaultCount8, setVaultCount8] = useState<number>(0);
  const [vaultCount9, setVaultCount9] = useState<number>(0);
  const [vaultCount10, setVaultCount10] = useState<number>(0);
  const [vaultCount11, setVaultCount11] = useState<number>(0);
  const [vaultSize0, setVaultSize0] = useState<number>(0);
  const [vaultSize1, setVaultSize1] = useState<number>(0);
  const [vaultSize2, setVaultSize2] = useState<number>(0);
  const [vaultSize3, setVaultSize3] = useState<number>(0);
  const [vaultSize4, setVaultSize4] = useState<number>(0);
  const [vaultSize5, setVaultSize5] = useState<number>(0);
  const [vaultSize6, setVaultSize6] = useState<number>(0);
  const [vaultSize7, setVaultSize7] = useState<number>(0);
  const [vaultSize8, setVaultSize8] = useState<number>(0);
  const [vaultSize9, setVaultSize9] = useState<number>(0);
  const [vaultSize10, setVaultSize10] = useState<number>(0);
  const [vaultSize11, setVaultSize11] = useState<number>(0);

  const refresh = async () => {
    const response = await authenticatedFetch(`${vaultdoorURL}api/vault`, {});
    switch (response.status) {
      case 200:
        const content = (await response.json()) as Array<VaultDescription>;
        if (Array.isArray(content)) {
          const reversed = content.reverse();
          setLoading(false);
          setKnownVaults(reversed);
        } else {
          console.error(
            "Expected server response to be an array, got ",
            content
          );
          setLoading(false);
        }
        break;
      default:
        const errorContent = await response.text();
        console.error(errorContent);
        setLoading(false);
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
        return content;
        break;
      default:
        const errorContent = await response.text();
        console.error(errorContent);
        return;
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
    <Paper className={classes.projectVaultData}>
      <Typography variant="h4">Archived Data</Typography>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Vault</TableCell>
            <TableCell>File Count</TableCell>
            <TableCell>Data Size</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {knownVaults.map(function (entry, idx) {
            fetchVaultData(entry.vaultId).then(function (res) {
              eval("setVaultCount" + idx)(res.total.count);
              eval("setVaultSize" + idx)(res.total.size);
            });

            return (
              <TableRow key={idx}>
                <TableCell>{entry.name}</TableCell>
                <TableCell>{eval("vaultCount" + idx)}</TableCell>
                <TableCell>{eval("vaultSize" + idx)}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </Paper>
  );
};

export default ProjectEntryVaultComponent;
