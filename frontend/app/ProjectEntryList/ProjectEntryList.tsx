import React, { useEffect, useState } from "react";
import ProjectEntryFilterComponent from "../filter/ProjectEntryFilterComponent.jsx";
import WorkingGroupEntryView from "../EntryViews/WorkingGroupEntryView.jsx";
import CommissionEntryView from "../EntryViews/CommissionEntryView.jsx";
import { RouteComponentProps } from "react-router-dom";
import moment from "moment";
import {
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  Paper,
  makeStyles,
  Button,
  IconButton,
  TablePagination,
  TableSortLabel,
} from "@material-ui/core";
import { getProjectsOnPage } from "./helpers";
import { isLoggedIn } from "../utils/api";
import { sortListByOrder, Order } from "../utils/utils";
import EditIcon from "@material-ui/icons/Edit";
import DeleteIcon from "@material-ui/icons/Delete";

interface HeaderTitles {
  label: string;
  key?: keyof Project;
}

const tableHeaderTitles: HeaderTitles[] = [
  { label: "Project title", key: "title" },
  { label: "Commission title", key: "commissionId" },
  { label: "Created", key: "created" },
  { label: "Group", key: "workingGroupId" },
  { label: "Status", key: "status" },
  { label: "Owner", key: "user" },
  { label: "" },
  { label: "Open" },
];
const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
  },
  createButton: {
    display: "flex",
    marginLeft: "auto",
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
});
const pageSizeOptions = [25, 50, 100];

const ProjectEntryList: React.FC<RouteComponentProps> = (props) => {
  const classes = useStyles();

  const [projects, setProjects] = useState<Project[]>([]);
  const [filterTerms, setFilterTerms] = useState<FilterTerms>({
    match: "W_ENDSWITH",
  });
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const [order, setOrder] = useState<Order>("asc");
  const [orderBy, setOrderBy] = useState<keyof Project>("title");

  const fetchProjectsOnPage = async () => {
    const projects = await getProjectsOnPage({
      page,
      pageSize,
      filterTerms,
    });
    setProjects(projects);
  };

  useEffect(() => {
    const fetchWhoIsLoggedIn = async () => {
      try {
        const loggedIn = await isLoggedIn();
        setIsAdmin(loggedIn.isAdmin);
        setFilterTerms(
          props.location.search.includes("mine")
            ? { user: loggedIn.uid, match: "W_EXACT" }
            : { match: "W_CONTAINS" }
        );
        fetchProjectsOnPage();
      } catch {
        setIsAdmin(false);
      }
    };

    fetchWhoIsLoggedIn();
  }, [page, pageSize]);

  const getActionIcons = (id: number) => {
    const componentName = props.location.pathname.split("/")[1];

    return (
      <span className="icons" style={{ display: isAdmin ? "inherit" : "none" }}>
        <IconButton
          onClick={() => props.history.push(`/${componentName}/${id}`)}
        >
          <EditIcon></EditIcon>
        </IconButton>
        <IconButton
          onClick={() => props.history.push(`/${componentName}/${id}/delete`)}
        >
          <DeleteIcon></DeleteIcon>
        </IconButton>
      </span>
    );
  };

  const getFilterComponent = () => {
    return (
      <ProjectEntryFilterComponent
        filterTerms={filterTerms}
        filterDidUpdate={filterDidUpdate}
      />
    );
  };

  const newElementCallback = (): void => {
    props.history.push("/project/new");
  };

  const filterDidUpdate = (newTerms: FilterTerms) => {
    setFilterTerms(newTerms);
    fetchProjectsOnPage();
  };

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

  const sortByColumn = (property: keyof Project) => (
    _event: React.MouseEvent<unknown>
  ) => {
    const isAsc = orderBy === property && order === "asc";
    setOrder(isAsc ? "desc" : "asc");
    setOrderBy(property);
  };

  return (
    <>
      {getFilterComponent()}
      <Button
        className={classes.createButton}
        variant="outlined"
        onClick={newElementCallback}
      >
        New
      </Button>
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
              {sortListByOrder(projects, order, orderBy).map(
                ({
                  id,
                  title,
                  commissionId,
                  created,
                  workingGroupId,
                  status,
                  user,
                }) => (
                  <TableRow key={id}>
                    <TableCell>{title}</TableCell>
                    <TableCell>
                      <CommissionEntryView entryId={commissionId} />
                    </TableCell>
                    <TableCell>
                      <span className="datetime">
                        {moment(created).format("DD/MM/YYYY HH:mm A")}
                      </span>
                    </TableCell>
                    <TableCell>
                      <WorkingGroupEntryView entryId={workingGroupId} />
                    </TableCell>
                    <TableCell>{status}</TableCell>
                    <TableCell>{user}</TableCell>
                    <TableCell>{getActionIcons(id)}</TableCell>
                    <TableCell>
                      {
                        <a target="_blank" href={`pluto:openproject:${id}`}>
                          Open project
                        </a>
                      }
                    </TableCell>
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
    </>
  );
};

export default ProjectEntryList;
