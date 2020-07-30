import {
  Button,
  IconButton,
  makeStyles,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  Typography,
} from "@material-ui/core";
import DeleteIcon from "@material-ui/icons/Delete";
import EditIcon from "@material-ui/icons/Edit";
import moment from "moment";
import React, { useEffect, useState } from "react";
import {
  RouteComponentProps,
  useHistory,
  useLocation,
  useParams,
} from "react-router-dom";
import CommissionEntryView from "../EntryViews/CommissionEntryView.jsx";
import WorkingGroupEntryView from "../EntryViews/WorkingGroupEntryView.jsx";
import ProjectEntryFilterComponent from "../filter/ProjectEntryFilterComponent.jsx";
import { isLoggedIn } from "../utils/api";
import { SortDirection, sortListByOrder } from "../utils/lists";
import { getProjectsOnPage } from "./helpers";

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

interface ProjectFilterTerms extends FilterTerms {
  commissionId?: number;
}

const ActionIcons: React.FC<{ id: number; isAdmin?: boolean }> = ({
  id,
  isAdmin = false,
}) => (
  <span
    className="icons"
    style={{ display: isAdmin ? "inherit" : "none", whiteSpace: "nowrap" }}
  >
    <IconButton href={`${deploymentRootPath}project/${id}`}>
      <EditIcon></EditIcon>
    </IconButton>
    <IconButton href={`${deploymentRootPath}project/${id}/delete`}>
      <DeleteIcon></DeleteIcon>
    </IconButton>
  </span>
);

const ProjectEntryList: React.FC<RouteComponentProps> = () => {
  // React Router
  const history = useHistory();
  const { search } = useLocation();
  const { commissionId } = useParams<{ commissionId?: string }>();

  // React state
  const [user, setUser] = useState<PlutoUser | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const [order, setOrder] = useState<SortDirection>("asc");
  const [orderBy, setOrderBy] = useState<keyof Project>("title");
  const [projects, setProjects] = useState<Project[]>([]);
  const [filterTerms, setFilterTerms] = useState<ProjectFilterTerms>({
    match: "W_CONTAINS",
  });

  // Material-UI
  const classes = useStyles();

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
        const user = await isLoggedIn();
        setUser(user);
      } catch (error) {
        console.error("Could not login user:", error);
      }
    };

    fetchWhoIsLoggedIn();
  }, []);

  useEffect(() => {
    const newFilterTerms: ProjectFilterTerms =
      user && new URLSearchParams(search).has("mine")
        ? { user: user.uid, match: "W_EXACT" }
        : { match: "W_CONTAINS" };

    const commissionIdAsNumber = Number(commissionId);

    if (
      commissionId !== undefined &&
      commissionId.length > 0 &&
      !Number.isNaN(commissionIdAsNumber)
    ) {
      newFilterTerms.commissionId = commissionIdAsNumber;
    }

    setFilterTerms(newFilterTerms);
  }, [commissionId, user?.uid]);

  useEffect(() => {
    if (!user) {
      return;
    }

    fetchProjectsOnPage();
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

  const sortByColumn = (property: keyof Project) => (
    _event: React.MouseEvent<unknown>
  ) => {
    const isAsc = orderBy === property && order === "asc";
    setOrder(isAsc ? "desc" : "asc");
    setOrderBy(property);
  };

  return (
    <>
      <ProjectEntryFilterComponent
        filterTerms={filterTerms}
        filterDidUpdate={setFilterTerms}
      />
      <Button
        className={classes.createButton}
        variant="outlined"
        onClick={() => {
          history.push("/project/new");
        }}
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
              {sortListByOrder(projects, orderBy, order).map(
                ({
                  id,
                  title,
                  commissionId,
                  created,
                  workingGroupId,
                  status,
                  user: projectUser,
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
                    <TableCell>{projectUser}</TableCell>
                    <TableCell>
                      <ActionIcons id={id} isAdmin={user?.isAdmin ?? false} />
                    </TableCell>
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
      {typeof commissionId === "string" && projects.length === 0 && (
        <Typography variant="subtitle1" style={{ marginTop: "1rem" }}>
          No projects for this commission.
        </Typography>
      )}
    </>
  );
};

export default ProjectEntryList;
