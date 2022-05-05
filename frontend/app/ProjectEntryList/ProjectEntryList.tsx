import { Button, IconButton, makeStyles, Paper, Grid } from "@material-ui/core";
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
import { buildFilterTerms, filterTermsToQuerystring } from "../filter/terms";

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

const ProjectEntryList: React.FC<RouteComponentProps> = () => {
  // React Router
  const history = useHistory<Project>();
  const { search } = useLocation();

  // React state
  const [user, setUser] = useState<PlutoUser | null>(null);
  const [pageSize, setPageSize] = useState<number>(25);
  const [page, setPage] = useState<number>(1);
  const [projects, setProjects] = useState<Project[]>([]);

  const [filterTerms, setFilterTerms] = useState<
    ProjectFilterTerms | undefined
  >(undefined);

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
    if (filterTerms) {
      fetchProjectsOnPage();
    }
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
    const currentURL = new URLSearchParams(search).toString();

    let newFilters = buildFilterTerms(currentURL, user);

    if (newFilters.title) {
      newFilters.match = "W_CONTAINS";
    }
    if (newFilters.user === "Mine" && user) {
      newFilters.user = user.uid;
    }

    console.log("filter terms set: ", newFilters);

    setFilterTerms(newFilters);
  }, [user]);

  return (
    <>
      <Helmet>
        <title>All Projects</title>
      </Helmet>
      <Grid container>
        {filterTerms ? (
          <Grid item>
            <ProjectFilterComponent
              filterTerms={filterTerms}
              filterDidUpdate={(newFilters: ProjectFilterTerms) => {
                console.log(
                  "ProjectFilterComponent filterDidUpdate ",
                  newFilters
                );
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
    </>
  );
};

export default ProjectEntryList;
