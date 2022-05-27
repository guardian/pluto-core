import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet";
import {
  Grid,
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
import { useGuardianStyles } from "~/misc/utils";
import axios from "axios";
import { Link } from "react-router-dom";
import moment from "moment";
import { SortDirection } from "~/utils/lists";

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

const tableHeaderTitles: HeaderTitle<Project>[] = [
  { label: "Obituary", key: "isObitProject" },
  { label: "Project", key: "title" },
  { label: "Created", key: "created" },
  { label: "Action" },
];

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

  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(25);
  const [orderBy, setOrderBy] = useState<keyof Project>("isObitProject");
  const [order, setOrder] = useState<SortDirection>("asc");

  const fetchObituaryProjects = async () => {
    try {
      const response = await axios.get(
        `/api/project/obits/sorted?startAt=${
          page * rowsPerPage
        }&limit=${rowsPerPage}&sort=${orderBy}&sortDirection=${order}`
      );
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

  useEffect(() => {
    fetchObituaryProjects();
  }, [page, rowsPerPage, order, orderBy]);

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
      <Helmet>
        <title>All Obituaries</title>
      </Helmet>
      {projects?.length ? (
        <>
          <Typography className={classes.obituariesTitle}>
            Obituaries
          </Typography>
          <TableContainer elevation={3} component={Paper}>
            <Table className={classes.table} aria-label="simple table">
              <TableHead>
                <TableRow>
                  {tableHeaderTitles.map((title, index) => (
                    <TableCell
                      key={title.label ? title.label : index}
                      sortDirection={order}
                      align={title.label == "Action" ? "right" : "left"}
                    >
                      {title.key ? (
                        <TableSortLabel
                          active={orderBy === title.key}
                          direction={orderBy === title.key ? order : "asc"}
                          onClick={sortByColumn(title.key)}
                        >
                          {title.label}
                        </TableSortLabel>
                      ) : (
                        title.label
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {projects.map((project: ObituaryProject) => (
                  <TableRow key={project.id}>
                    <TableCell component="th" scope="row">
                      {project.isObitProject}
                    </TableCell>
                    <TableCell>{project.title}</TableCell>
                    <TableCell>
                      <span className="datetime">
                        {moment(project.created).format("DD/MM/YYYY HH:mm")}
                      </span>
                    </TableCell>
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
          <TablePagination
            component="div"
            count={-1}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
            labelDisplayedRows={({ from, to }) => `${from}-${to}`}
          />
        </>
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
