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
  CircularProgress,
} from "@material-ui/core";
import { authenticatedFetch } from "./auth";
import KeyboardArrowUpIcon from "@material-ui/icons/KeyboardArrowUp";
import KeyboardArrowDownIcon from "@material-ui/icons/KeyboardArrowDown";
import { loadAllVaultData, VaultState } from "../vaultdoor/vaultdoor";

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
  onError?: (errorDesc: string) => void;
}

const ProjectEntryVaultComponent: React.FC<ProjectEntryVaultComponentProps> = (
  props
) => {
  const classes = useStyles();
  const [loading, setLoading] = useState(false);
  const [didLoad, setDidLoad] = useState(false);
  const { project } = props;
  const [knownVaults, setKnownVaults] = useState<Array<VaultDescription>>([]);
  const [data, setData] = useState<VaultState[]>([]);
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
          if (props.onError) {
            props.onError(
              "Could not load archive data, see console for details"
            );
          }
          setLoading(false);
        }
        break;
      default:
        const errorContent = await response.text();
        console.error(errorContent);
        if (props.onError)
          props.onError("Could not load archive data, see console for details");
        setLoading(false);
        break;
    }
  };

  useEffect(() => {
    if (open && !didLoad) {
      setLoading(true);
      fetchVaults().catch((err) => {
        console.error("Could not load vaults: ", err);
        if (props.onError)
          props.onError("Could not load archive data, see console for details");
      });
    }
  }, [open]);

  useEffect(() => {
    if (knownVaults.length == 0) return; //don't bother to attempt data load if there is nothing to load in...
    console.log(`Loading in data for ${knownVaults.length} vaults...`);

    loadAllVaultData(vaultdoorURL, project, knownVaults)
      .then((loadedData) => {
        setData(loadedData);
        setDidLoad(true);
      })
      .catch((err) => {
        console.error("Could not load data: ", err);
      });
  }, [knownVaults]);

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
              id="archive-expander-button"
              onClick={() => setOpen(!open)}
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
        {loading ? (
          <CircularProgress id="loading-spinner" />
        ) : (
          <TableContainer id="vaults-table">
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Vault</TableCell>
                  <TableCell>File Count</TableCell>
                  <TableCell>Data Size</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data
                  .filter((v) => v.fileCount > 0)
                  .map((vault, idx) => (
                    <TableRow
                      id={`data-${vault.vaultName.replace(" ", "-")}`}
                      hover={true}
                      onClick={() => {
                        window.open(
                          `${vaultdoorURL}byproject?project=${project.id}`,
                          "_blank"
                        );
                      }}
                      key={idx}
                    >
                      <TableCell>{vault.vaultName}</TableCell>
                      <TableCell>{vault.fileCount}</TableCell>
                      <TableCell>{humanFileSize(vault.totalSize)}</TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Collapse>
    </Paper>
  );
};

export default ProjectEntryVaultComponent;
