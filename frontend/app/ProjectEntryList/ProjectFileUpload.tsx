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
    setResultDialogMessage(
      "Error uploading file: " +
        errorMessage +
        "\nPlease contact multimediatech@guardian.co.uk"
    );
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
        if (!file.name.endsWith(".prproj")) {
          handleUploadError("Only .prproj files are supported");
          setIsUploading(false);
          return;
        }
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
            handleUploadError(errorMessage);
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
      <Dialog
        open={openDialog}
        onClose={handleCloseDialog}
        aria-labelledby="update-file-dialog-title"
        aria-describedby="update-file-dialog-description"
      >
        <DialogTitle id="update-file-dialog-title">
          Confirm Project File Update
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="update-file-dialog-description">
            <strong>
              Are you sure you want to update the project file in Pluto?
            </strong>
            <br />
            You are about to upload a new project file. This action will
            overwrite the current file in Pluto.
            <br />
            <br />
            <strong>Please Note:</strong>
            <br />
            Uploading a new file will overwrite the existing project file. This
            action is suitable if you have updated the project file externally
            (e.g., on a personal device) and need to synchronize these changes
            with Pluto. Any existing file will be automatically backed up prior
            to this update.
            <br />
            <br />
            <strong>Important:</strong>
            <br />
            Ensure you are uploading the correct file to avoid unintended file
            replacement.
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

      {isUploading && (
        <div
          style={{
            position: "fixed", // Use fixed to position relative to the viewport
            top: "50%", // 50% from the top
            left: "50%", // 50% from the left
            transform: "translate(-50%, -50%)", // Adjust to perfectly center
            textAlign: "center", // Center the text inside the div
            zIndex: 1000, // Ensure it's on top of other elements
            background: "white", // Optional: for better visibility
            padding: "20px", // Optional: some padding around the content
            borderRadius: "10px", // Optional: rounded corners
            boxShadow: "0px 0px 10px rgba(0, 0, 0, 0.5)", // Optional: drop shadow for a floating eff
          }}
        >
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
