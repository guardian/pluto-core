import React, { useEffect, useState } from "react";
import {
  Paper,
  Typography,
  makeStyles,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableBody,
  TableCell,
  Tooltip,
  IconButton,
  Collapse,
  Grid,
} from "@material-ui/core";
import { authenticatedFetch } from "./auth";
import KeyboardArrowUpIcon from "@material-ui/icons/KeyboardArrowUp";
import KeyboardArrowDownIcon from "@material-ui/icons/KeyboardArrowDown";

const useStyles = makeStyles({
  projectVaultData: {
    display: "flex",
    flexDirection: "column",
    padding: "1rem",
    marginTop: "1rem",
  },
  loading: {
    display: "flex",
    flexDirection: "column",
    width: "100%",
    alignItems: "center",
  },
  archiveButton: {
    width: "50px",
    height: "50px",
  },
  archiveIcon: {
    width: "60px",
    height: "60px",
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
  const [open, setOpen] = useState<boolean>(false);

  const fetchVaults = async () => {
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
    fetchVaults();
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

  const humanFileSize = (bytes: number, si = false, dp = 1) => {
    const thresh = si ? 1000 : 1024;

    if (Math.abs(bytes) < thresh) {
      return bytes + " B";
    }

    const units = si
      ? ["kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"]
      : ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"];
    let u = -1;
    const r = 10 ** dp;

    do {
      bytes /= thresh;
      ++u;
    } while (
      Math.round(Math.abs(bytes) * r) / r >= thresh &&
      u < units.length - 1
    );

    return bytes.toFixed(dp) + " " + units[u];
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
      <Grid container spacing={3}>
        <Grid item xs={11}>
          <Typography variant="h4">Archived Data</Typography>
        </Grid>
        <Grid item xs={1}>
          <Tooltip title="Show archived data">
            <IconButton
              className={classes.archiveButton}
              aria-label="expand data"
              size="small"
              onClick={() => {
                setOpen(!open);
              }}
            >
              {open ? (
                <KeyboardArrowUpIcon className={classes.archiveIcon} />
              ) : (
                <KeyboardArrowDownIcon className={classes.archiveIcon} />
              )}
            </IconButton>
          </Tooltip>
        </Grid>
      </Grid>
      <Collapse in={open} timeout="auto" unmountOnExit>
        <TableContainer>
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
                  <TableRow
                    hover={true}
                    onClick={() => {
                      window.open(
                        `${vaultdoorURL}byproject?project=${project.id}`,
                        "_blank"
                      );
                    }}
                    key={idx}
                  >
                    <TableCell>{entry.name}</TableCell>
                    <TableCell>{eval("vaultCount" + idx)}</TableCell>
                    <TableCell>
                      {humanFileSize(eval("vaultSize" + idx))}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Collapse>
    </Paper>
  );
};

export default ProjectEntryVaultComponent;
