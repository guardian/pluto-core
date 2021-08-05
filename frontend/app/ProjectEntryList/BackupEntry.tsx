import React, { useEffect, useState } from "react";
import {
  ListItem,
  ListItemIcon,
  ListItemText,
  makeStyles,
  Size,
  Typography,
} from "@material-ui/core";
import { FileCopy } from "@material-ui/icons";
import { getFileStorageMetadata } from "./helpers";
import { format, parseISO } from "date-fns";
import { DEFAULT_DATE_FORMAT } from "../../types/constants";
import SizeFormatter from "../common/SizeFormatter";

interface BackupEntryProps {
  fileId: number;
  filepath: string;
  version: number;
}

const useStyles = makeStyles((theme) => ({
  emphasis: {
    fontWeight: theme.typography.fontWeightBold,
  },
}));

const BackupEntry: React.FC<BackupEntryProps> = (props) => {
  const classes = useStyles();
  const [fileMeta, setFileMeta] = useState<Map<string, string>>(new Map());
  const [loadError, setLoadError] = useState(false);

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
            <Typography variant="caption">
              File size is <SizeFormatter bytes={fileMeta.get("size")} /> and
              was backed up at {fileMeta.get("lastModified")}
            </Typography>
          )}
        </>
      </ListItemText>
    </ListItem>
  );
};

export default BackupEntry;