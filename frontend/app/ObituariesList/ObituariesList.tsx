import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet";
import {
  CircularProgress,
  Grid,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from "@material-ui/core";
import { useGuardianStyles } from "~/misc/utils";
import axios from "axios";
import { Link } from "react-router-dom";

export interface ObituaryProject {
  commissionId: number;
  created: string;
  deep_archive: boolean;
  deletable: boolean;
  id: number;
  isObitProject: string;
  productionOffice: string;
  projectTypeId: number;
  sensitive: boolean;
  status: string;
  title: string;
  updated: string;
  user: string;
  workingGroupId: number;
}

const ObituariesList = () => {
  const classes = useGuardianStyles();
  const [projects, setProjects] = useState<ObituaryProject[] | []>([]);
  function createData(
    name: string,
    calories: number,
    fat: number,
    carbs: number,
    protein: number
  ) {
    return { name, calories, fat, carbs, protein };
  }

  const fetchObituaryProjects = async () => {
    try {
      const response = await axios.get("/api/project/obits/sorted");
      const data = response?.data;
      const projects: ObituaryProject[] = data.result;
      setProjects(projects);
      console.log({ projects });
    } catch (error) {
      console.error({ error });
    }
  };

  useEffect(() => {
    fetchObituaryProjects();
  }, []);

  return (
    <>
      <Helmet>
        <title>All Obituaries</title>
      </Helmet>
      {projects?.length ? (
        <TableContainer elevation={3} component={Paper}>
          <Table className={classes.table} aria-label="simple table">
            <TableHead>
              <TableRow>
                <TableCell>Obituary</TableCell>
                <TableCell>Project</TableCell>
                <TableCell align="right">Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {projects.map((project: ObituaryProject) => (
                <TableRow key={project.id}>
                  <TableCell component="th" scope="row">
                    {project.isObitProject}
                  </TableCell>
                  <TableCell>{project.title}</TableCell>
                  <TableCell align="right">
                    <Link to={"/project/" + project.id}>
                      Edit obituary project
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      ) : (
        <Grid container justifyContent="center" alignItems="center">
          <Grid item xs spacing={4}>
            No obituaries in the system.
          </Grid>
        </Grid>
      )}
    </>
  );
};

export default ObituariesList;
