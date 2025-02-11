import React, { useEffect, useState } from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  ListItem,
  ListItemIcon,
  ListItemText,
  Typography,
} from "@material-ui/core";
import { FileCopy } from "@material-ui/icons";
import { getFileStorageMetadata } from "./helpers";
import { format, parseISO } from "date-fns";
import { DEFAULT_DATE_FORMAT } from "../../types/constants";
import SizeFormatter from "../common/SizeFormatter";
import { useGuardianStyles } from "~/misc/utils";
import axios from "axios";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";

interface BackupEntryProps {
  fileId: number;
  filepath: string;
  version: number;
  premiereVersion?: number;
  isAdmin: boolean;
  projectId?: number;
}

const BackupEntry: React.FC<BackupEntryProps> = (props) => {
  const classes = useGuardianStyles();
  const [fileMeta, setFileMeta] = useState<Map<string, string>>(new Map());
  const [loadError, setLoadError] = useState(false);
  const [openDialog, setOpenDialog] = useState(false);

  useEffect(() => {
    getFileStorageMetadata(props.fileId)
      .then((info) => {
        const maybeISOTime = info.get("lastModified");
        if (maybeISOTime) {
          try {
            const d = parseISO(maybeISOTime);
            info.set("lastModified", format(d, DEFAULT_DATE_FORMAT));
          } catch (err) {
            console.log(`Could not parse date value ${maybeISOTime}: ${err}`);
          }
        }
        setFileMeta(info);
      })
      .catch((err) => {
        console.error("Could not load file information: ", err);
        setLoadError(true);
      });
  }, [props.fileId]);

  const handleClickOpenDialog = () => {
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
  };

  const handleConfirmUpload = () => {
    handleCloseDialog();
    handleRestore();
  };

  const handleRestore = async () => {
    try {
      const request =
        "/api/project/" + props.projectId + "/restore/" + props.version;
      const response = await axios.put(request, null, {
        headers: {
          "Content-Type": "application/json",
        },
      });
      console.log(response.data);
      SystemNotification.open(
        SystemNotifcationKind.Success,
        `${response.data.detail}`
      );
    } catch (error) {
      console.error("Error restoring file:", error);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Failed to restore project: ${error}`
      );
    }
  };

  return (
    <ListItem>
      <ListItemIcon>
        <FileCopy />
      </ListItemIcon>
      <ListItemText disableTypography={true}>
        <>
          <Typography className={classes.emphasis}>{props.filepath}</Typography>
          <Typography>Version {props.version}</Typography>
          {loadError ? (
            <Typography variant="caption">
              Could not load file information, see console for details
            </Typography>
          ) : (
            <>
              <Typography variant="caption">
                File size is <SizeFormatter bytes={fileMeta.get("size")} /> and
                was backed up at {fileMeta.get("lastModified")}
              </Typography>
              {props.premiereVersion ? (
                <Typography variant="caption">
                  {" "}
                  This is a Premiere project at internal version{" "}
                  {props.premiereVersion}
                </Typography>
              ) : undefined}
            </>
          )}
        </>
      </ListItemText>
      {props.isAdmin ? (
        <div>
          <Button
            color="secondary"
            variant="contained"
            onClick={handleClickOpenDialog}
          >
            Restore
          </Button>
          {/* Confirmation Dialog */}
          <Dialog
            open={openDialog}
            onClose={handleCloseDialog}
            aria-labelledby="update-file-dialog-title"
            aria-describedby="update-file-dialog-description"
          >
            <DialogTitle id="update-file-dialog-title">
              Confirm Project File Restore
            </DialogTitle>
            <DialogContent>
              <DialogContentText id="update-file-dialog-description">
                <strong>
                  Are you sure you want to restore the project file?
                </strong>
                <br />
                You are about to restore a backed up project file. This action
                will overwrite the current file.
                <br />
                <br />
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <Button onClick={handleCloseDialog} color="primary">
                Cancel
              </Button>
              <Button onClick={handleConfirmUpload} color="primary" autoFocus>
                Proceed
              </Button>
            </DialogActions>
          </Dialog>
        </div>
      ) : null}
    </ListItem>
  );
};

export default BackupEntry;
