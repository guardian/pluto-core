import React, { useEffect, useState } from "react";
import {
  Typography,
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
import { useGuardianStyles } from "~/misc/utils";

interface CommissionEntryDeliverablesComponentProps {
  commission: CommissionFullRecord;
  searchString: string;
}

const ActionIcons: React.FC<{ id: number }> = (props) => (
  <span className="icons">
    <IconButton href={`/deliverables/project/${props.id}`}>
      <EditIcon />
    </IconButton>
  </span>
);

const CommissionEntryDeliverablesComponent: React.FC<CommissionEntryDeliverablesComponentProps> = (
  props
) => {
  const classes = useGuardianStyles();

  const [loading, setLoading] = useState<boolean>(true);
  const { id } = props.commission;
  const [bundles, setBundles] = useState<DeliverableBundle[]>([]);

  const fetchBundles = async () => {
    await setLoading(true);

    try {
      const server_response = await axios.get(
        `/deliverables/api/bundle/commission/${id}`
      );
      return Promise.all([setBundles(server_response.data), setLoading(false)]);
    } catch (error) {
      return Promise.all([setLoading(false)]);
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
            {bundles
              .filter((bundle) =>
                bundle.name
                  .toLowerCase()
                  .includes(props.searchString.toLowerCase())
              )
              .map((entry, idx) => (
                <TableRow key={idx}>
                  <TableCell>{entry.name}</TableCell>
                  <TableCell>{entry.pluto_core_project_id}</TableCell>
                  <TableCell>
                    {moment(entry.created).format("DD/MM/YYYY HH:mm A")}
                  </TableCell>
                  <TableCell>
                    <ActionIcons id={entry.pluto_core_project_id} />
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
