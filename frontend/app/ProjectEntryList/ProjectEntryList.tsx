import { Button, Paper, Grid } from "@material-ui/core";
import React, { useEffect, useState } from "react";
import { RouteComponentProps, useHistory, useLocation } from "react-router-dom";
import ProjectFilterComponent from "../filter/ProjectFilterComponent.jsx";
import { isLoggedIn } from "../utils/api";
import { getProjectsOnPage } from "./helpers";
import ProjectsTable from "./ProjectsTable";
import { Helmet } from "react-helmet";
import { buildFilterTerms, filterTermsToQuerystring } from "../filter/terms";
import { useGuardianStyles } from "~/misc/utils";
import {SortDirection} from "~/utils/lists";

const ProjectEntryList: React.FC<RouteComponentProps> = () => {
  // React Router
  const history = useHistory<Project>();
  const { search } = useLocation();

  // React state
  const [user, setUser] = useState<PlutoUser | null>(null);
  const [pageSize, setPageSize] = useState<number>(25);
  const [page, setPage] = useState<number>(1);
  const [order, setOrder] = useState<SortDirection>("desc");
  const [orderBy, setOrderBy] = useState<keyof Project>("created");
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectCount, setProjectCount] = useState<number>(0);

  const [filterTerms, setFilterTerms] = useState<
    ProjectFilterTerms | undefined
  >(undefined);

  // Material-UI
  const classes = useGuardianStyles();

  const fetchProjectsOnPage = async () => {
    const mineOnly = new URLSearchParams(search).has("mine");
    if (mineOnly && !user) {
      console.log(
        "Requested 'my' projects but no user set, waiting until it has been"
      );
      return;
    }

    const [projects, count] = await getProjectsOnPage({
      page,
      pageSize,
      filterTerms: filterTerms,
      order,
      orderBy,
    });

    setProjects(projects);
    setProjectCount(count);
  };

  useEffect(() => {
    if (filterTerms) {
      fetchProjectsOnPage();
    }
  }, [filterTerms, page, pageSize, order, orderBy]);

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
    const isMineInURL = currentURL.includes("mine");
    let newFilters = buildFilterTerms(currentURL);

    if (isMineInURL) {
      newFilters = buildFilterTerms(currentURL, user);
    }

    if (newFilters.title) {
      newFilters.match = "W_CONTAINS";
    }
    if (newFilters.user === "Mine" && user) {
      newFilters.user = user.uid;
    }

    console.log("filter terms set: ", newFilters);

    setFilterTerms(newFilters);
  }, [user?.uid]);

  const generateUserName = (inputString: string) => {
    if (inputString.includes("@")) {
      const splitString = inputString.split("@", 1)[0];
      const userNameConst = splitString.replace(".", "_");
      return userNameConst;
    }
    return inputString;
  };

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
        <Grid className={classes.buttonGrid}>
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
          updateRequired={(page, pageSize, order, orderBy) => {
            console.log("ProjectsTable updateRequired");
            setPageSize(pageSize);
            setPage(page);
            setOrder(order);
            setOrderBy(orderBy);
          }}
          projects={projects}
          projectCount={projectCount}
          user={user}
        />
      </Paper>
    </>
  );
};

export default ProjectEntryList;
