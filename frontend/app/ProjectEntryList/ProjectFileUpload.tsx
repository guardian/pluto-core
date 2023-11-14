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
import { Alert } from "@material-ui/lab";

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

  const handleUploadSuccess = (message: React.SetStateAction<string>) => {
    setSnackbarMessage(message);
    setSnackbarSeverity("success");
    setOpenSnackbar(true);
  };

  const handleUploadError = (errorMessage: string) => {
    setSnackbarMessage("Error uploading file: " + errorMessage);
    setSnackbarSeverity("error");
    setOpenSnackbar(true);
  };

  useEffect(() => {
    getFileData(props.projectId).then(setFileData);
  }, [props.projectId]);

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
            handleUploadSuccess("File uploaded successfully!");
          } catch (error) {
            console.error("Error uploading file:", error);
            const errorMessage =
              error instanceof Error ? error.message : String(error);
            handleUploadError("Error uploading file: " + errorMessage);
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
        <DialogTitle>Confirm Upload</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to upload this file?
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

      <Snackbar
        open={openSnackbar}
        autoHideDuration={6000}
        onClose={() => setOpenSnackbar(false)}
      >
        <Alert
          onClose={() => setOpenSnackbar(false)}
          severity={snackbarSeverity}
        >
          {snackbarMessage}
        </Alert>
      </Snackbar>

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
