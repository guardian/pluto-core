import React, { useEffect, useState } from "react";
import {
  Typography,
  makeStyles,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableBody,
  TableCell,
  IconButton,
} from "@material-ui/core";
import axios from "axios";
import moment from "moment";
import EditIcon from "@material-ui/icons/Edit";

const useStyles = makeStyles({
  loading: {
    display: "flex",
    flexDirection: "column",
    width: "100%",
    alignItems: "center",
  },
});

interface CommissionEntryDeliverablesComponentProps {
  commission: CommissionFullRecord;
}

const ActionIcons: React.FC<{ id: string }> = (props) => (
  <span className="icons">
    <IconButton href={`/deliverables/project/${props.id}`}>
      <EditIcon />
    </IconButton>
  </span>
);

const CommissionEntryDeliverablesComponent: React.FC<CommissionEntryDeliverablesComponentProps> = (
  props
) => {
  const classes = useStyles();

  const [loading, setLoading] = useState<boolean>(true);
  const { id } = props.commission;
  const [bundles, setBundles] = useState<Bundle[]>([]);
  const [lastError, setLastError] = useState<object | null>(null);

  const fetchBundles = async () => {
    await setLoading(true);

    try {
      const server_response = await axios.get(
        `/deliverables/api/bundle/commission/${id}`
      );
      return Promise.all([
        setBundles(server_response.data),
        setLoading(false),
        setLastError(null),
      ]);
    } catch (error) {
      return Promise.all([setLastError(error), setLoading(false)]);
    }
  };

  useEffect(() => {
    fetchBundles();
  }, []);

  if (loading) {
    return (
      <div className={classes.loading}>
        <Typography variant="h4">Loading...</Typography>
      </div>
    );
  }

  return (
    <>
      <TableContainer>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Project Id.</TableCell>
              <TableCell>Created</TableCell>
              <TableCell>Open</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {bundles.map((entry, idx) => (
              <TableRow key={idx}>
                <TableCell>{entry.name}</TableCell>
                <TableCell>{entry.pluto_core_project_id}</TableCell>
                <TableCell>
                  {moment(entry.created).format("DD/MM/YYYY HH:mm A")}
                </TableCell>
                <TableCell>
                  <ActionIcons id={entry.pluto_core_project_id.toString()} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </>
  );
};

export default CommissionEntryDeliverablesComponent;
