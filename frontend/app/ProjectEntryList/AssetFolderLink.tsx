import React, { useEffect, useState } from "react";
import axios from "axios";
import { Button, Typography } from "@material-ui/core";

interface AssetFolderLinkProps {
  projectId: number;
}

const AssetFolderLink: React.FC<AssetFolderLinkProps> = (props) => {
  //state
  const [loading, setLoading] = useState<boolean>(false);
  const [assetFolderPath, setAssetFolderPath] = useState<string>("");
  const [showCreate, setShowCreate] = useState<boolean>(false);

  const loadData = async () => {
    setLoading(true);

    try {
      const response = await axios.get(
        `/api/project/${props.projectId}/assetfolder`
      );
      const result = response.data as PlutoApiResponse<ProjectMetadataResponse>;
      setAssetFolderPath(encodeURIComponent(result.result.value));
      setShowCreate(false);
      setLoading(false);
    } catch (err) {
      setLoading(false);
      if (err.response) {
        if (err.response.status === 404) {
          //no asset folder found
          setShowCreate(true);
        }
      } else {
        console.error(
          `Could not load asset folder information for ${props.projectId}`,
          err
        );
      }
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const requestNewAssetFolder = async () => {
    alert("This is not implemented in the backend yet");
  };

  return (
    <>
      {loading ? (
        <Typography variant="caption">...</Typography>
      ) : showCreate ? (
        <span style={{ marginLeft: "1em" }}>
          <Typography>No asset folder found</Typography>
          <Button onClick={requestNewAssetFolder} variant="outlined">
            Create
          </Button>
        </span>
      ) : (
        <Button
          style={{ marginLeft: "1em" }}
          href={`pluto:openfolder:${assetFolderPath}`}
          variant="contained"
        >
          Asset folder
        </Button>
      )}
    </>
  );
};

export default AssetFolderLink;
