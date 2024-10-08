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
  TableSortLabel,
  Grid,
  Menu,
  MenuItem,
} from "@material-ui/core";
import React, { useEffect, useState } from "react";
import { useHistory, useLocation } from "react-router-dom";
import { getCommissionsOnPage, getWorkingGroupNameMap } from "./helpers";
import { SortDirection } from "../utils/lists";
import { Helmet } from "react-helmet";
import ProjectFilterComponent from "../filter/ProjectFilterComponent.jsx";
import { isLoggedIn } from "../utils/api";
import { buildFilterTerms, filterTermsToQuerystring } from "../filter/terms";
import { useGuardianStyles } from "~/misc/utils";

const tableHeaderTitles: HeaderTitle<Commission>[] = [
  { label: "Title", key: "title" },
  { label: "Projects", key: "projectCount" },
  { label: "Created", key: "created" },
  { label: "Group", key: "workingGroupId" },
  { label: "Status", key: "status" },
  { label: "Owner", key: "owner" },
];

declare var deploymentRootPath: string;
const pageSizeOptions = [25, 50, 100];

const CommissionsList: React.FC = () => {
  const classes = useGuardianStyles();

  const [commissions, setCommissions] = useState<Commission[]>([]);
  const [workingGroups, setWorkingGroups] = useState<Map<number, string>>(
    new Map()
  );
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const [order, setOrder] = useState<SortDirection>("desc");
  const [orderBy, setOrderBy] = useState<keyof Commission>("created");

  const history = useHistory();
  const [filterTerms, setFilterTerms] = useState<
    ProjectFilterTerms | undefined
  >(undefined);
  const [user, setUser] = useState<PlutoUser | null>(null);
  const { search } = useLocation();
  const [commissionToOpen, setCommissionToOpen] = useState<number>(1);

  useEffect(() => {
    if (filterTerms != undefined) {
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
    }
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
        let user = await isLoggedIn();
        user.uid = generateUserName(user.uid);
        setUser(user);
      } catch (error) {
        console.error("Could not login user:", error);
      }
    };

    fetchWhoIsLoggedIn();
  }, []);

  useEffect(() => {
    const currentURL = new URLSearchParams(search).toString();
    let newFilters = buildFilterTerms(currentURL);
    if (newFilters.user === "Mine" && user) {
      newFilters.user = user.uid;
    }
    console.log("Filter terms set: ", newFilters);
    setFilterTerms(newFilters);
  }, [user]);

  const generateUserName = (inputString: string) => {
    if (inputString.includes("@")) {
      const splitString = inputString.split("@", 1)[0];
      const userNameConst = splitString.replace(".", "_");
      return userNameConst;
    }
    return inputString;
  };

  const [contextMenu, setContextMenu] = React.useState<{
    mouseX: number;
    mouseY: number;
  } | null>(null);

  const handleContextMenu = (event: React.MouseEvent, commission: number) => {
    event.preventDefault();
    setCommissionToOpen(commission);
    setContextMenu(
      contextMenu === null
        ? {
            mouseX: event.clientX + 2,
            mouseY: event.clientY - 6,
          }
        : null
    );
  };

  const handleClose = () => {
    setContextMenu(null);
  };

  const userAllowed = (confidential: Boolean, commissionUser: string) => {
    if (confidential == undefined) {
      return true;
    }
    if (confidential == false) {
      return true;
    }
    if (user != null) {
      if (user.isAdmin) {
        return true;
      } else if (
        commissionUser
          .split("|")
          .includes(generateUserName(user.uid).toLowerCase())
      ) {
        return true;
      } else {
        return false;
      }
    } else {
      return true;
    }
  };

  return (
    <>
      <Helmet>
        <title>All Commissions</title>
      </Helmet>
      <Grid container justifyContent="space-between">
        {filterTerms ? (
          <Grid item>
            <ProjectFilterComponent
              filterTerms={filterTerms}
              filterDidUpdate={(newFilters: ProjectFilterTerms) => {
                const updatedUrlParams = filterTermsToQuerystring(newFilters);
                if (newFilters.user === "Everyone") {
                  newFilters.user = undefined;
                }
                if (newFilters.user === "Mine" && user) {
                  newFilters.user = user.uid;
                }
                setFilterTerms(newFilters);

                history.push("?" + updatedUrlParams);
              }}
            />
          </Grid>
        ) : null}
        <Grid className={classes.buttonGrid} style={{ marginLeft: "auto" }}>
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
              {commissions.map(
                ({
                  id,
                  title,
                  projectCount,
                  created,
                  workingGroupId,
                  status,
                  owner,
                  confidential,
                }) => {
                  if (userAllowed(confidential, owner)) {
                    return (
                      <TableRow
                        hover={true}
                        onClick={() => {
                          window.open(
                            `${deploymentRootPath}commission/${id}`,
                            "_blank"
                          );
                        }}
                        key={id}
                        onContextMenu={(e) => {
                          handleContextMenu(e, id);
                        }}
                      >
                        <TableCell>{title}</TableCell>
                        <TableCell>{projectCount}</TableCell>
                        <TableCell>
                          {new Date(created).toLocaleString()}
                        </TableCell>
                        <TableCell>
                          {workingGroups.get(workingGroupId) ?? "<Unknown>"}
                        </TableCell>
                        <TableCell>{status}</TableCell>
                        <TableCell>{owner.replace(/\|/g, " ")}</TableCell>
                      </TableRow>
                    );
                  } else {
                    return null;
                  }
                }
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
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
          // FIXME: remove when count is correct
          labelDisplayedRows={({ from, to }) => `${from}-${to}`}
        />
      </Paper>
      <Menu
        open={contextMenu !== null}
        onClose={handleClose}
        anchorReference="anchorPosition"
        anchorPosition={
          contextMenu !== null
            ? { top: contextMenu.mouseY, left: contextMenu.mouseX }
            : undefined
        }
      >
        <MenuItem
          onClick={(e) => {
            window.open(
              `${deploymentRootPath}commission/${commissionToOpen}`,
              "_blank"
            );
            handleClose();
          }}
        >
          Open in new window or tab
        </MenuItem>
        <MenuItem
          onClick={(e) => {
            history.push(`/commission/${commissionToOpen}`);
            handleClose();
          }}
        >
          Open in existing window or tab
        </MenuItem>
      </Menu>
    </>
  );
};

export default CommissionsList;
