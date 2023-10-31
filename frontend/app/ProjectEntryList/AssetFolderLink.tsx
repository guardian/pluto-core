import React, { useEffect, useState } from "react";
import axios from "axios";
import { Button, Typography, IconButton, Tooltip } from "@material-ui/core";
import ReplayIcon from "@material-ui/icons/Replay";
import FolderIcon from "@material-ui/icons/Folder";

interface AssetFolderLinkProps {
  projectId: number;
  onClick: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
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
  }, [props.projectId]);

  const requestNewAssetFolder = async (event: {
    stopPropagation: () => void;
  }) => {
    event.stopPropagation();
    loadData();
  };

  return (
    <>
      {loading ? (
        <Typography variant="caption">...</Typography>
      ) : showCreate ? (
        <>
          <Typography>
            No asset folder found
            <Tooltip title="Check again">
              <IconButton
                onClick={(event) => requestNewAssetFolder(event)}
                style={{ marginLeft: "1em" }}
              >
                <ReplayIcon />
              </IconButton>
            </Tooltip>
          </Typography>
        </>
      ) : (
        <Button
          startIcon={<FolderIcon />}
          style={{ minWidth: "160px", minHeight: "35px" }}
          href={`pluto:openfolder:${assetFolderPath}`}
          variant="contained"
          onClick={props.onClick}
        >
          Asset&nbsp;folder
        </Button>
      )}
    </>
  );
};

export default AssetFolderLink;
