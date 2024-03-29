import React, { useEffect, useState } from "react";
import {
  ListItem,
  ListItemIcon,
  ListItemText,
  Typography,
} from "@material-ui/core";
import { FileCopy } from "@material-ui/icons";
import { getAssetFolderFileStorageMetadata } from "./helpers";
import { format, parseISO } from "date-fns";
import { DEFAULT_DATE_FORMAT } from "../../types/constants";
import SizeFormatter from "../common/SizeFormatter";
import { useGuardianStyles } from "~/misc/utils";

interface AssetFolderBackupEntryProps {
  fileId: number;
  filepath: string;
  version: number;
}

const AssetFolderBackupEntry: React.FC<AssetFolderBackupEntryProps> = (
  props
) => {
  const classes = useGuardianStyles();
  const [fileMeta, setFileMeta] = useState<Map<string, string>>(new Map());
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    getAssetFolderFileStorageMetadata(props.fileId)
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
            </>
          )}
        </>
      </ListItemText>
    </ListItem>
  );
};

export default AssetFolderBackupEntry;
