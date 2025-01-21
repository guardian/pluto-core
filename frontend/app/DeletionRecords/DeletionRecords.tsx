import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
import {
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  Paper,
  TablePagination,
  TableSortLabel,
} from "@material-ui/core";
import { getDeletionRecordsOnPage } from "./helpers";
import { sortListByOrder, SortDirection } from "../utils/lists";
import { isLoggedIn } from "../utils/api";
import { Helmet } from "react-helmet";
import { useGuardianStyles } from "~/misc/utils";
import moment from "moment";

const tableHeaderTitles: HeaderTitle<DeletionRecord>[] = [
  { label: "Project", key: "projectEntry" },
  { label: "Owner", key: "user" },
  { label: "Deleted", key: "deleted" },
  { label: "Created", key: "created" },
  { label: "Working Group", key: "workingGroup" },
];

const pageSizeOptions = [25, 50, 100];

const DeletionRecords: React.FC<RouteComponentProps> = (props) => {
  const classes = useGuardianStyles();

  const [deletionRecords, setDeletionRecords] = useState<DeletionRecord[]>([]);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const [order, setOrder] = useState<SortDirection>("asc");
  const [orderBy, setOrderBy] = useState<keyof DeletionRecord>("id");

  useEffect(() => {
    const fetchDeletionRecordsOnPage = async () => {
      const workingGroups = await getDeletionRecordsOnPage({ page, pageSize });
      setDeletionRecords(workingGroups);
    };

    const fetchWhoIsLoggedIn = async () => {
      try {
        const loggedIn = await isLoggedIn();
        setIsAdmin(loggedIn.isAdmin);
      } catch {
        setIsAdmin(false);
      }
    };

    fetchWhoIsLoggedIn();

    fetchDeletionRecordsOnPage();
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

  const sortByColumn = (property: keyof DeletionRecord) => (
    _event: React.MouseEvent<unknown>
  ) => {
    const isAsc = orderBy === property && order === "asc";
    setOrder(isAsc ? "desc" : "asc");
    setOrderBy(property);
  };

  return (
    <>
      <Helmet>
        <title>Deletion Records - Pluto Admin</title>
      </Helmet>
      {isAdmin ? (
        <Paper elevation={3}>
          <TableContainer>
            <Table className={classes.table}>
              <TableHead>
                <TableRow>
                  {tableHeaderTitles.map((title, index) => (
                    <TableCell
                      key={title.label ? title.label : index}
                      sortDirection={orderBy === title.key ? order : false}
                    >
                      {title.key ? (
                        <TableSortLabel
                          active={orderBy === title.key}
                          direction={orderBy === title.key ? order : "asc"}
                          onClick={sortByColumn(title.key)}
                        >
                          {title.label}
                          {orderBy === title.key && (
                            <span className={classes.visuallyHidden}>
                              {order === "desc"
                                ? "sorted descending"
                                : "sorted ascending"}
                            </span>
                          )}
                        </TableSortLabel>
                      ) : (
                        title.label
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {sortListByOrder(deletionRecords, orderBy, order).map(
                  ({
                    id,
                    projectEntry,
                    user,
                    deleted,
                    created,
                    workingGroup,
                  }) => (
                    <TableRow
                      hover={true}
                      onClick={() => props.history.push(`/deleted/${id}`)}
                      key={id}
                    >
                      <TableCell>{projectEntry}</TableCell>
                      <TableCell>{user}</TableCell>
                      <TableCell>
                        <span className="datetime">
                          {moment(deleted).format("DD/MM/YYYY HH:mm")}
                        </span>
                      </TableCell>
                      <TableCell>
                        <span className="datetime">
                          {moment(created).format("DD/MM/YYYY HH:mm")}
                        </span>
                      </TableCell>
                      <TableCell>{workingGroup}</TableCell>
                    </TableRow>
                  )
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <TablePagination
            rowsPerPageOptions={pageSizeOptions}
            component="div"
            count={-1}
            rowsPerPage={pageSize}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
            labelDisplayedRows={({ from, to }) => `${from}-${to}`}
          />
        </Paper>
      ) : (
        <div>You do not have access to this page.</div>
      )}
    </>
  );
};

export default DeletionRecords;
