import React, { useEffect, useRef, useState } from "react";
import IconButton from "@material-ui/core/IconButton";
import CloudUpload from "@material-ui/icons/CloudUpload";
import Tooltip from "@material-ui/core/Tooltip";
import { makeStyles } from "@material-ui/core/styles";
import axios from "axios";
import { createHash } from "crypto";
import { getFileData } from "./helpers";

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

        // Now we can upload the file along with the checksum
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
            // Handle response here
          } catch (error) {
            console.error("Error uploading file:", error);
            // Handle error here
          }
        }
      };
      reader.readAsArrayBuffer(file);
    }
  };

  return (
    // console.log("File data: ", fileData[0].id),
    <>
      <Tooltip title="Update Premiere Pro project file">
        <IconButton
          disableRipple
          className={classes.noHoverEffect}
          onClick={handleUploadClick}
        >
          <CloudUpload />
        </IconButton>
      </Tooltip>
      <input
        type="file"
        style={{ display: "none" }}
        ref={fileInputRef}
        onChange={handleFileChange}
      />
    </>
  );
};

export default UploadButton;
