import React, { useEffect, useState } from "react";
import ProjectEntryFilterComponent from "../filter/ProjectEntryFilterComponent.jsx";
import WorkingGroupEntryView from "../EntryViews/WorkingGroupEntryView.jsx";
import CommissionEntryView from "../EntryViews/CommissionEntryView.jsx";
import { Link, RouteComponentProps } from "react-router-dom";
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
  TablePagination,
} from "@material-ui/core";
import { getProjectsOnPage, isLoggedIn } from "./helpers";

const tableHeaderTitles = [
  "Project title",
  "Commission title",
  "Created",
  "Group",
  "Status",
  "Owner",
  "",
  "Open",
];
const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
    "& .MuiTableRow-hover": {
      cursor: "pointer",
    },
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
        <Link to={"/" + componentName + "/" + id}>
          <img className="smallicon" src="/assets/images/edit.png" />
        </Link>
        <Link to={"/" + componentName + "/" + id + "/delete"}>
          <img className="smallicon" src="/assets/images/delete.png" />
        </Link>
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

  return (
    <>
      {getFilterComponent()}
      <span className="banner-control">
        <button id="newElementButton" onClick={newElementCallback}>
          New
        </button>
      </span>
      <TableContainer elevation={3} component={Paper}>
        <Table className={classes.table}>
          <TableHead>
            <TableRow>
              {tableHeaderTitles.map((title, index) => (
                <TableCell key={title ? title : index}>{title}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {projects.map(
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
                    {<CommissionEntryView entryId={commissionId} />}
                  </TableCell>
                  <TableCell>
                    {
                      <span className="datetime">
                        {moment(created).format("DD/MM/YYYY HH:mm A")}
                      </span>
                    }
                  </TableCell>
                  <TableCell>
                    {<WorkingGroupEntryView entryId={workingGroupId} />}
                  </TableCell>
                  <TableCell>{status}</TableCell>
                  <TableCell>{user}</TableCell>
                  <TableCell>{getActionIcons(id)}</TableCell>
                  <TableCell>
                    {
                      <a target="_blank" href={"pluto:openproject:" + id}>
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
    </>
  );
};

export default ProjectEntryList;
