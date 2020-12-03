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
  Grid,
} from "@material-ui/core";
import React, { useEffect, useState } from "react";
import {
  RouteComponentProps,
  useHistory,
  useLocation,
  useParams,
} from "react-router-dom";
import ProjectFilterComponent from "../filter/ProjectFilterComponent.jsx";
import { isLoggedIn } from "../utils/api";
import { getProjectsOnPage, updateProjectOpenedStatus } from "./helpers";
import ProjectsTable from "./ProjectsTable";
import { Helmet } from "react-helmet";

const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
    "& .MuiTableRow-root": {
      cursor: "pointer",
    },
  },
  createButton: {
    display: "flex",
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
  buttonGrid: {
    marginLeft: "auto",
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
  const [page, setPage] = useState<number>(1);
  const [projects, setProjects] = useState<Project[]>([]);

  const [filterTerms, setFilterTerms] = useState<ProjectFilterTerms>({
    match: "W_CONTAINS",
    showKilled: false,
  });

  // Material-UI
  const classes = useStyles();

  const fetchProjectsOnPage = async () => {
    const mineOnly = new URLSearchParams(search).has("mine");
    if (mineOnly && !user) {
      console.log(
        "Requested 'my' projects but no user set, waiting until it has been"
      );
      return;
    }

    const projects = await getProjectsOnPage({
      page,
      pageSize,
      filterTerms: filterTerms,
    });

    setProjects(projects);
  };

  useEffect(() => {
    fetchProjectsOnPage();
  }, [filterTerms, page, pageSize]);

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
        ? { user: user.uid, match: "W_EXACT", showKilled: false }
        : { match: "W_CONTAINS", showKilled: false };

    const commissionIdAsNumber = Number(commissionId);

    if (
      commissionId !== undefined &&
      commissionId.length > 0 &&
      !Number.isNaN(commissionIdAsNumber)
    ) {
      newFilterTerms.commissionId = commissionIdAsNumber;
    }
    console.log("filter terms set: ", newFilterTerms);
    setFilterTerms(newFilterTerms);
  }, [commissionId, user?.uid]);

  return (
    <>
      <Helmet>
        <title>All Projects</title>
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
            isProject={true}
          />
        </Grid>
        <Grid item className={classes.buttonGrid}>
          <Button
            className={classes.createButton}
            variant="contained"
            color="primary"
            onClick={() => {
              history.push("/project/new");
            }}
          >
            New
          </Button>
        </Grid>
      </Grid>
      <Paper elevation={3}>
        <ProjectsTable
          className={classes.table}
          pageSizeOptions={[25, 50, 100]}
          updateRequired={(page, pageSize) => {
            console.log("ProjectsTable updateRequired");
            setPageSize(pageSize);
            setPage(page);
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
