import React, { useEffect, useState } from "react";
import {
  CircularProgress,
  Grid,
  IconButton,
  TableCell,
  TableRow,
} from "@material-ui/core";
import { format, parseISO } from "date-fns";
import { Launch } from "@material-ui/icons";
import { SystemNotifcationKind, SystemNotification } from "pluto-headers";
import { getProject, getProjectType } from "../ProjectEntryList/helpers";
import axios from "axios";

interface ValidationTableRowProps {
  data: ValidationProblem;
}

const ValidationTableRow: React.FC<ValidationTableRowProps> = (props) => {
  const [loading, setLoading] = useState(false);
  const [projectTitle, setProjectTitle] = useState("");

  const getFile: (id: number) => Promise<FileEntry> = async (
    fileId: number
  ) => {
    const response = await axios.get<PlutoApiResponse<FileEntry>>(
      `/api/file/${fileId}`
    );
    return response.data.result;
  };

  const formattedDate = (dateStr: string) => {
    try {
      const parsedDate = parseISO(dateStr);
      return format(parsedDate, "eeeeee do MMM yyy");
    } catch (err) {
      console.log(`Could not parse date value ${dateStr}: ${err}`);
      return "";
    }
  };

  useEffect(() => {
    switch (props.data.entityClass) {
      case "ProjectEntry":
        setLoading(true);
        getProject(props.data.entityId)
          .then((projectInfo) => {
            getProjectType(projectInfo.projectTypeId)
              .then((projectTypeInfo) => {
                setProjectTitle(
                  `${projectInfo.title} - ${formattedDate(
                    projectInfo.created
                  )} - ${projectTypeInfo.name} ${
                    projectTypeInfo.targetVersion
                  } - ${projectInfo.productionOffice}`
                );
                setLoading(false);
              })
              .catch((err) => {
                console.log(
                  `Could not get project type information for type id ${projectInfo.projectTypeId}: `,
                  err
                );
                setProjectTitle(
                  `${projectInfo.title} - ${formattedDate(
                    projectInfo.created
                  )} - (unknown type ${projectInfo.projectTypeId}) - ${
                    projectInfo.productionOffice
                  }`
                );
                setLoading(false);
              });
          })
          .catch((err) => {
            console.error(
              `Could not load info for project with id ${props.data.entityId}: `,
              err
            );
            setProjectTitle("(no title)");
            setLoading(false);
          });
        break;
      case "FileEntry":
        setLoading(true);
        getFile(props.data.entityId)
          .then((fileInfo) => {
            setProjectTitle(
              `${fileInfo.filepath} on ${fileInfo.storageid}, hasContent?: ${fileInfo.hasContent}`
            );
            setLoading(false);
          })
          .catch((err) => {
            console.error(
              `Could not load info for file with id ${props.data.entityId}`,
              err
            );
            setProjectTitle("(no info)");
            setLoading(false);
          });
        break;
      default:
        break;
    }
  }, [props.data]);

  const safeTimestamp = () => {
    try {
      const timestamp = parseISO(props.data.timestamp);
      return format(timestamp, "HH:mm:ss EEE do MMM yyyy");
    } catch (err) {
      console.error(
        `Could not reformat timestamp ${props.data.timestamp}: `,
        err
      );
      return "(invalid)";
    }
  };

  const jumpToEntity = () => {
    switch (props.data.entityClass) {
      case "ProjectEntry":
        window.open(`/pluto-core/project/${props.data.entityId}`);
        break;
      default:
        SystemNotification.open(
          SystemNotifcationKind.Info,
          `Can't open an entry of type ${props.data.entityClass}`
        );
        break;
    }
  };

  return (
    <TableRow>
      <TableCell>{safeTimestamp()}</TableCell>
      <TableCell>
        <Grid container justify="space-between">
          <Grid item>
            <Grid container direction="column">
              <Grid item>
                {props.data.entityClass} {props.data.entityId}
              </Grid>
              <Grid item>{loading ? <CircularProgress /> : projectTitle}</Grid>
            </Grid>
          </Grid>
          <Grid item>
            <IconButton onClick={jumpToEntity}>
              <Launch />
            </IconButton>
          </Grid>
        </Grid>
      </TableCell>
      <TableCell>{props.data.notes}</TableCell>
    </TableRow>
  );
};

export default ValidationTableRow;
