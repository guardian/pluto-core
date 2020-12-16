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
  makeStyles,
  TableSortLabel,
  Grid,
} from "@material-ui/core";
import React, { useEffect, useState } from "react";
import { useHistory } from "react-router-dom";
import { getCommissionsOnPage, getWorkingGroupNameMap } from "./helpers";
import { sortListByOrder, SortDirection } from "../utils/lists";
import { Helmet } from "react-helmet";
import ProjectFilterComponent from "../filter/ProjectFilterComponent.jsx";
import { isLoggedIn } from "../utils/api";

const tableHeaderTitles: HeaderTitle<Commission>[] = [
  { label: "Title", key: "title" },
  { label: "Projects", key: "projectCount" },
  { label: "Created", key: "created" },
  { label: "Group", key: "workingGroupId" },
  { label: "Status", key: "status" },
  { label: "Owner", key: "owner" },
];

const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
    "& .MuiTableRow-hover": {
      cursor: "pointer",
    },
  },
  createButton: {
    display: "flex",
    marginBottom: "0.625rem",
  },
  visuallyHidden: {
    border: 0,
    clip: "rect(0 0 0 0)",
    height: 1,
    margin: -1,
    overflow: "hidden",
    padding: 0,
    position: "absolute",
    top: 20,
    width: 1,
  },
  buttonGrid: {
    marginLeft: "auto",
  },
});

interface ProjectFilterTerms extends FilterTerms {
  commissionId?: number;
}

const pageSizeOptions = [25, 50, 100];

const CommissionsList: React.FC = () => {
  const classes = useStyles();

  const [commissions, setCommissions] = useState<Commission[]>([]);
  const [workingGroups, setWorkingGroups] = useState<Map<number, string>>(
    new Map()
  );
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const [order, setOrder] = useState<SortDirection>("desc");
  const [orderBy, setOrderBy] = useState<keyof Commission>("created");

  const history = useHistory();
  const [filterTerms, setFilterTerms] = useState<ProjectFilterTerms>({
    match: "W_CONTAINS",
    showKilled: false,
  });
  const [user, setUser] = useState<PlutoUser | null>(null);

  useEffect(() => {
    const updateCommissions = async () => {
      const commissions = await getCommissionsOnPage({
        page,
        pageSize,
        filterTerms: filterTerms,
        order,
        orderBy,
      });
      const workingGroups = await getWorkingGroupNameMap(commissions);
      setCommissions(commissions);
      setWorkingGroups(workingGroups);
    };

    updateCommissions();
  }, [filterTerms, page, pageSize, order, orderBy]);

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

  const sortByColumn = (property: keyof Commission) => (
    _event: React.MouseEvent<unknown>
  ) => {
    const isAsc = orderBy === property && order === "asc";
    setOrder(isAsc ? "desc" : "asc");
    setOrderBy(property);
  };

  useEffect(() => {
    const fetchWhoIsLoggedIn = async () => {
      try {
        const user = await isLoggedIn();
        setUser(user);
      } catch (error) {
        console.error("Could not login user:", error);
      }
    };

    fetchWhoIsLoggedIn();
  }, []);

  return (
    <>
      <Helmet>
        <title>All Commissions</title>
      </Helmet>
      <Grid container>
        <Grid item>
          <ProjectFilterComponent
            filterTerms={filterTerms}
            filterDidUpdate={(newFilters: ProjectFilterTerms) => {
              console.log(
                "ProjectFilterComponent filterDidUpdate ",
                newFilters
              );
              if (newFilters.user === "Everyone") {
                newFilters.user = undefined;
              }
              if (newFilters.user === "Mine" && user) {
                newFilters.user = user.uid;
              }
              setFilterTerms(newFilters);
            }}
          />
        </Grid>
        <Grid item className={classes.buttonGrid}>
          <Button
            className={classes.createButton}
            variant="contained"
            color="primary"
            onClick={() => {
              history.push("/commission/new");
            }}
          >
            New
          </Button>
        </Grid>
      </Grid>
      <Paper elevation={3}>
        <TableContainer>
          <Table className={classes.table}>
            <TableHead>
              <TableRow>
                {tableHeaderTitles.map((title) => (
                  <TableCell
                    key={title.label}
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
              {sortListByOrder(commissions, orderBy, order).map(
                ({
                  id,
                  title,
                  projectCount,
                  created,
                  workingGroupId,
                  status,
                  owner,
                }) => (
                  <TableRow
                    hover={true}
                    onClick={() => {
                      history.push(`/commission/${id}`);
                    }}
                    key={id}
                  >
                    <TableCell>{title}</TableCell>
                    <TableCell>{projectCount}</TableCell>
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
        />
      </Paper>
    </>
  );
};

export default CommissionsList;
