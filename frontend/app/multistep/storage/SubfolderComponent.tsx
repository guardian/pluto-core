import React from "react";
import { TextField, Typography } from "@material-ui/core";

interface StorageSubfolderComponentProps {
  currentStorage: StorageType;
  rootPathWasSet: (newValue: string) => void;
  rootPath: string;
  clientPathWasSet: (newValue: string) => void;
  clientPath: string;
}

const StorageSubfolderComponent: React.FC<StorageSubfolderComponentProps> = (
  props
) => {
  return (
    <>
      <Typography variant="h3">Storage Subfolder</Typography>
      {props.currentStorage.hasSubFolders ? (
        <>
          <table>
            <tbody>
              <tr>
                <td style={{ verticalAlign: "bottom" }}>Subfolder path</td>
                <td>
                  <TextField
                    value={props.rootPath}
                    label="Folder path"
                    onChange={(event) =>
                      props.rootPathWasSet(event.target.value)
                    }
                  />
                </td>
              </tr>
              <tr>
                <td style={{ verticalAlign: "bottom" }}>
                  Client mount point (if any)
                </td>
                <td>
                  <TextField
                    value={props.clientPath}
                    label="Client path"
                    onChange={(event) =>
                      props.clientPathWasSet(event.target.value)
                    }
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </>
      ) : (
        <>
          <Typography className="information">
            {props.currentStorage.name} does not support subfolders
          </Typography>
        </>
      )}
    </>
  );
};

export default StorageSubfolderComponent;
