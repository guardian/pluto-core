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
import React, { useEffect, useState } from "react";
import {
  RouteComponentProps,
  useHistory,
  useLocation,
  useParams,
} from "react-router-dom";
import ProjectEntryFilterComponent from "../filter/ProjectEntryFilterComponent.jsx";
import { isLoggedIn } from "../utils/api";
import { getProjectsOnPage, updateProjectOpenedStatus } from "./helpers";
import ProjectsTable from "./ProjectsTable";

const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
    "& .MuiTableRow-root": {
      cursor: "pointer",
    },
  },
  createButton: {
    display: "flex",
    marginLeft: "auto",
    marginBottom: "0.625rem",
  },
  openProjectButton: {
    whiteSpace: "nowrap",
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

interface ProjectFilterTerms extends FilterTerms {
  commissionId?: number;
}

const ProjectEntryList: React.FC<RouteComponentProps> = () => {
  // React Router
  const history = useHistory<Project>();
  const { search } = useLocation();
  const { commissionId } = useParams<{ commissionId?: string }>();

  // React state
  const [user, setUser] = useState<PlutoUser | null>(null);
  const [pageSize, setPageSize] = useState<number>(25);

  const [projects, setProjects] = useState<Project[]>([]);
  const [filterTerms, setFilterTerms] = useState<ProjectFilterTerms>({
    match: "W_CONTAINS",
  });
  const [firstLoad, setFirstLoad] = useState<boolean>(true);

  // Material-UI
  const classes = useStyles();

  const fetchProjectsOnPage = async (
    page: number,
    pageSize: number,
    updatedFilterTerms?: ProjectFilterTerms
  ) => {
    if (firstLoad) {
      const fetchWhoIsLoggedInAndSetProjects = async () => {
        try {
          const user = await isLoggedIn();
          setUser(user);
          if (user && new URLSearchParams(search).has("mine")) {
            const projects = await getProjectsOnPage({
              page,
              pageSize,
              filterTerms: { user: user.uid, match: "W_EXACT" },
            });

            setProjects(projects);
          } else {
            const projects = await getProjectsOnPage({
              page,
              pageSize,
              filterTerms: filterTerms,
            });

            setProjects(projects);
          }
        } catch (error) {
          console.error("Could not get user:", error);
        }
      };

      fetchWhoIsLoggedInAndSetProjects();
    } else {
      const projects = await getProjectsOnPage({
        page,
        pageSize,
        filterTerms: updatedFilterTerms ?? filterTerms,
      });

      setProjects(projects);
    }
  };

  useEffect(() => {
    setFirstLoad(false);
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

  return (
    <>
      <ProjectEntryFilterComponent
        filterTerms={filterTerms}
        filterDidUpdate={(newFilters: ProjectFilterTerms) => {
          fetchProjectsOnPage(0, pageSize, newFilters);
          setFilterTerms(newFilters);
        }}
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
        <ProjectsTable
          className={classes.table}
          pageSizeOptions={[25, 50, 100]}
          updateRequired={(page, pageSize) => {
            setPageSize(pageSize);
            return fetchProjectsOnPage(page, pageSize, filterTerms);
          }}
          projects={projects}
        />
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
