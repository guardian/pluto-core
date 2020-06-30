import {
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
} from "@material-ui/core";
import React, { useEffect, useState } from "react";
import { useHistory } from "react-router-dom";
import { getCommissionsOnPage, getWorkingGroupNameMap } from "./helpers";

import "./CommissionsList.scss";

const tableHeaderTitles = [
  "Title",
  "Projects",
  "Created",
  "Group",
  "Status",
  "Owner",
];

const pageSizeOptions = [25, 50, 100];

const CommissionsList: React.FC = () => {
  const [commissions, setCommissions] = useState<Commission[]>([]);
  const [workingGroups, setWorkingGroups] = useState<Map<number, string>>(
    new Map()
  );
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const history = useHistory();

  useEffect(() => {
    const updateCommissions = async () => {
      const commissions = await getCommissionsOnPage({
        page,
        pageSize,
      });
      const workingGroups = await getWorkingGroupNameMap(commissions);
      setCommissions(commissions);
      setWorkingGroups(workingGroups);
    };

    updateCommissions();
  }, [page, pageSize]);

  const handleChangePage = (
    _event: React.MouseEvent<HTMLButtonElement, MouseEvent> | null,
    newPage: number
  ) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = async (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    setRowsPerPage(+event.target.value);
    setPage(0);
  };

  // TODO: for use later?
  // const [uid, setUid] = useState(null);
  // const [isAdmin, setIsAdmin] = useState(false);
  // const [filterEnabled, setFilterEnabled] = useState(false);
  // useEffect(async () => {
  //   try {
  //     await loadDependencies({
  //       setIsAdmin,
  //       setUid,
  //     });
  //   } catch (error) {
  //     console.log(error);
  //   }
  // }, []);

  return (
    <Paper>
      <Button href={"/commission/new"}>New</Button>
      <TableContainer>
        <Table>
          <TableHead>
            <TableRow>
              {tableHeaderTitles.map((title) => (
                <TableCell key={title}>{title}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {commissions.map(
              ({ id, title, created, workingGroupId, status, owner }) => (
                <TableRow
                  className={"table-row"}
                  key={id}
                  onClick={() => {
                    history.push(`/commission/${id}`);
                  }}
                  hover
                >
                  <TableCell>{title}</TableCell>
                  <TableCell>TODO: Project count</TableCell>
                  <TableCell>{new Date(created).toLocaleString()}</TableCell>
                  <TableCell>
                    {workingGroups.get(workingGroupId) ?? "<Unknown>"}
                  </TableCell>
                  <TableCell>{status}</TableCell>
                  <TableCell>{owner}</TableCell>
                </TableRow>
              )
            )}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        rowsPerPageOptions={pageSizeOptions}
        component="div"
        // FIXME: count = -1 causes the pagination component to be able to
        // walk past the last page, which displays zero rows. Need an endpoint
        // which returns the total, or is returned along the commissions data.
        count={-1}
        rowsPerPage={pageSize}
        page={page}
        onChangePage={handleChangePage}
        onChangeRowsPerPage={handleChangeRowsPerPage}
        // FIXME: remove when count is correct
        labelDisplayedRows={({ from, to }) => `${from}-${to}`}
      ></TablePagination>
    </Paper>
  );
};

export default CommissionsList;
