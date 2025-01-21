import React, { useEffect, useState } from "react";
import {
  Typography,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableBody,
  TableCell,
} from "@material-ui/core";
import axios from "axios";
import moment from "moment";
import { useGuardianStyles } from "~/misc/utils";

interface CommissionEntryDeliverablesComponentProps {
  commission: CommissionFullRecord;
  searchString: string;
}

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
              <TableCell>Items</TableCell>
              <TableCell>Created</TableCell>
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
                <TableRow
                  key={idx}
                  onClick={() =>
                    window.open(
                      `/deliverables/project/${entry.pluto_core_project_id}`,
                      "_blank"
                    )
                  }
                  hover
                  style={{ cursor: "pointer" }}
                >
                  <TableCell>{entry.name}</TableCell>
                  <TableCell>{entry.pluto_core_project_id}</TableCell>
                  <TableCell>{entry.total_assets}</TableCell>
                  <TableCell>
                    {moment(entry.created).format("DD/MM/YYYY HH:mm A")}
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
