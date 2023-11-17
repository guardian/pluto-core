import React, { useEffect, useRef, useState } from "react";
import IconButton from "@material-ui/core/IconButton";
import CloudUpload from "@material-ui/icons/CloudUpload";
import Tooltip from "@material-ui/core/Tooltip";
import { makeStyles } from "@material-ui/core/styles";
import axios from "axios";
import { createHash } from "crypto";
import { getFileData } from "./helpers";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Snackbar,
} from "@material-ui/core";
import CircularProgress from "@material-ui/core/CircularProgress";
import HelpOutlineIcon from "@material-ui/icons/HelpOutline";

const API = "/api";
const API_PROJECTS = `${API}/project`;
const API_FILES = `${API}/file`;

interface ProjectFileUploadProps {
  projectId: number;
}
const useStyles = makeStyles((theme) => ({
  noHoverEffect: {
    "&:hover": {
      backgroundColor: "transparent",
    },
  },
}));

const UploadButton: React.FC<ProjectFileUploadProps> = (props) => {
  const classes = useStyles();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [checksum, setChecksum] = useState<string>("");
  const [fileData, setFileData] = useState<FileEntry[]>([]);

  const [openDialog, setOpenDialog] = useState(false);
  const [openSnackbar, setOpenSnackbar] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [snackbarSeverity, setSnackbarSeverity] = useState<
    "error" | "warning" | "info" | "success"
  >("info");

  const [showResultDialog, setShowResultDialog] = useState(false);
  const [resultDialogMessage, setResultDialogMessage] = useState("");

  const handleUploadSuccess = () => {
    setResultDialogMessage("File uploaded successfully!");
    setShowResultDialog(true);
  };

  const handleUploadError = (errorMessage: string) => {
    setResultDialogMessage("Error uploading file: " + errorMessage);
    setShowResultDialog(true);
  };

  const handleCloseResultDialog = () => {
    setShowResultDialog(false);
  };

  const handleClickOpenDialog = () => {
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
  };

  const handleConfirmUpload = () => {
    handleCloseDialog();
    handleUploadClick();
  };

  useEffect(() => {
    getFileData(props.projectId).then(setFileData);
  }, [props.projectId]);

  const [isUploading, setIsUploading] = useState(false);

  const handleUploadClick = () => {
    // Trigger the file input click event
    fileInputRef.current?.click();
  };

  const handleFileChange = async (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const files = event.target.files;
    if (files && files.length > 0) {
      const file = files[0];
      setIsUploading(true);

      // Calculate the checksum
      const reader = new FileReader();
      reader.onload = async (e) => {
        const contents = e.target?.result as ArrayBuffer;
        const hash = createHash("sha256");
        hash.update(new Uint8Array(contents));
        const calculatedChecksum = hash.digest("hex");

        const formData = new FormData();
        formData.append("file", file);
        formData.append("sha256", calculatedChecksum);

        console.log("Checksum: ", calculatedChecksum); // Log the calculated checksum
        if (fileData[0].id) {
          try {
            const request = API_FILES + "/" + fileData[0].id + "/content";
            console.log("Request: ", request);
            const response = await axios.post(request, formData, {
              headers: {
                "Content-Type": "multipart/form-data",
              },
            });
            console.log(response.data);
            handleUploadSuccess();
            setIsUploading(false);
          } catch (error) {
            console.error("Error uploading file:", error);
            const errorMessage =
              error instanceof Error ? error.message : String(error);
            handleUploadError("Error uploading file: " + errorMessage);
            setIsUploading(false);
          }
        }
      };
      reader.readAsArrayBuffer(file);
    }
  };

  return (
    <>
      {/* Confirmation Dialog */}
      <Dialog open={openDialog} onClose={handleCloseDialog}>
        <DialogTitle>Confirm Project File Update</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to upload a new Project file to replace the
            main existing project file that is on Pluto?
            <Tooltip
              title="Use this option if you have worked on this project file outside of Pluto such as on a laptop or elsewhere, and you now want to update the Pluto project file with a newer version. The replaced Project file will be backed up"
              placement="right"
            >
              <IconButton>
                <HelpOutlineIcon />
              </IconButton>
            </Tooltip>
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog} color="primary">
            Cancel
          </Button>
          <Button onClick={handleConfirmUpload} color="primary">
            Upload
          </Button>
        </DialogActions>
      </Dialog>

      {isUploading && (
        <div style={{ textAlign: "center" }}>
          <CircularProgress />
          <p>Uploading file...</p>
        </div>
      )}

      <Dialog
        open={showResultDialog}
        onClose={handleCloseResultDialog}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title">{"Upload Status"}</DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            {resultDialogMessage}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseResultDialog} color="primary" autoFocus>
            Close
          </Button>
        </DialogActions>
      </Dialog>

      <input
        type="file"
        style={{ display: "none" }}
        ref={fileInputRef}
        onChange={handleFileChange}
      />

      <Tooltip title="Update Premiere Pro project file">
        <IconButton
          disableRipple
          className={classes.noHoverEffect}
          onClick={handleClickOpenDialog}
        >
          <CloudUpload />
        </IconButton>
      </Tooltip>
    </>
  );
};

export default UploadButton;
