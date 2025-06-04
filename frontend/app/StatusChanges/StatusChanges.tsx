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
import { getStatusChangesOnPage } from "./helpers";
import { sortListByOrder, SortDirection } from "../utils/lists";
import { isLoggedIn } from "../utils/api";
import { Helmet } from "react-helmet";
import { useGuardianStyles } from "~/misc/utils";
import moment from "moment";

const tableHeaderTitles: HeaderTitle<StatusChange>[] = [
  { label: "Project", key: "projectId" },
  { label: "Title", key: "title" },
  { label: "User", key: "user" },
  { label: "Time", key: "time" },
  { label: "Status", key: "status" },
];

declare var deploymentRootPath: string;

const pageSizeOptions = [10, 50, 100];

const StatusChanges: React.FC<RouteComponentProps> = (props) => {
  const classes = useGuardianStyles();

  const [statusChanges, setStatusChanges] = useState<StatusChange[]>([]);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const [order, setOrder] = useState<SortDirection>("desc");
  const [orderBy, setOrderBy] = useState<keyof StatusChange>("id");

  useEffect(() => {
    const fetchStatusChangesOnPage = async () => {
      const statusChanges = await getStatusChangesOnPage({ page, pageSize });
      setStatusChanges(statusChanges);
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

    fetchStatusChangesOnPage();
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

  const sortByColumn = (property: keyof StatusChange) => (
    _event: React.MouseEvent<unknown>
  ) => {
    const isAsc = orderBy === property && order === "asc";
    setOrder(isAsc ? "desc" : "asc");
    setOrderBy(property);
  };

  return (
    <>
      <Helmet>
        <title>Status Changes - Pluto Admin</title>
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
                {sortListByOrder(statusChanges, orderBy, order).map(
                  ({ id, projectId, time, user, status, title }) => (
                    <TableRow
                      hover={true}
                      onClick={() =>
                        window.open(
                          `${deploymentRootPath}project/${projectId}`,
                          "_blank"
                        )
                      }
                      key={id}
                    >
                      <TableCell>{projectId}</TableCell>
                      <TableCell>{title}</TableCell>
                      <TableCell>{user}</TableCell>
                      <TableCell>
                        <span className="datetime">
                          {moment(time).format("DD/MM/YYYY HH:mm")}
                        </span>
                      </TableCell>
                      <TableCell>{status}</TableCell>
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

export default StatusChanges;
