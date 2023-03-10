import React from "react";
import { TextField, Typography } from "@material-ui/core";
import { useGuardianStyles } from "~/misc/utils";

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
  const classes = useGuardianStyles();

  return (
    <>
      <Typography variant="h3">Storage Subfolder</Typography>
      {props.currentStorage.hasSubFolders ? (
        <>
          <table className={classes.subfolderTable}>
            <tbody>
              <tr>
                <td className={classes.labelCell}>Subfolder path</td>
                <td>
                  <TextField
                    value={props.rootPath}
                    className={classes.fullWidth}
                    label="Folder path"
                    onChange={(event) =>
                      props.rootPathWasSet(event.target.value)
                    }
                  />
                </td>
              </tr>
              <tr>
                <td className={classes.labelCell}>
                  Client mount point (if any)
                </td>
                <td>
                  <TextField
                    value={props.clientPath}
                    className={classes.fullWidth}
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
