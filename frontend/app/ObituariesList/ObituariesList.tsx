import React, { ChangeEvent, useEffect, useMemo, useState } from "react";
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
  FormControl,
  Box,
  Button,
  TextField,
} from "@material-ui/core";
import { useGuardianStyles } from "~/misc/utils";
import axios from "axios";
import { Link } from "react-router-dom";
import moment from "moment";
import { SortDirection } from "~/utils/lists";
import ProjectTypeDisplay from "~/common/ProjectTypeDisplay";
import {
  openProject,
  updateProjectOpenedStatus,
} from "~/ProjectEntryList/helpers";
import { SystemNotifcationKind, SystemNotification } from "@guardian/pluto-headers";
import AssetFolderLink from "~/ProjectEntryList/AssetFolderLink";
import CommissionEntryView from "../EntryViews/CommissionEntryView";
import { Autocomplete } from "@material-ui/lab";

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
  { label: "Commission" },
  { label: "Created", key: "created" },
  { label: "Type" },
  { label: "Owner" },
  { label: "Action" },
  { label: "Open" },
];

const ObituariesList = () => {
  const classes = useGuardianStyles();
  const [projects, setProjects] = useState<ObituaryProject[] | []>([]);
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(25);
  const [orderBy, setOrderBy] = useState<keyof Project>("isObitProject");
  const [order, setOrder] = useState<SortDirection>("asc");
  const [name, setName] = useState<string>("");
  const [obituaryOptions, setObituaryOptions] = useState<null | string[]>(null);

  const fetchObituaryProjects = async () => {
    try {
      const args: { [key: string]: string | undefined } = {
        name: name == "" ? undefined : name.toLowerCase(),
        startAt: (page * rowsPerPage).toString(),
        limit: rowsPerPage.toString(),
        sort: orderBy,
        sortDirection: order,
      };

      const queryString = Object.keys(args)
        .filter((k) => !!args[k])
        .map((k) => `${k}=${encodeURIComponent(args[k] as string)}`) //typecast is safe because we already filtered out null values
        .join("&");

      const response = await axios.get<{ result: ObituaryProject[] }>(
        `/api/project/obits?${queryString}`
      );
      setProjects(response.data.result);
    } catch (error) {
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Could not load obituaries: ${error}`
      );
      console.error({ error });
    }
  };

  useEffect(() => {
    fetchObituaryProjects();
  }, [page, rowsPerPage, order, orderBy, name]);

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

  const searchObits = useMemo(() => {
    const prefixString =
      name != "" ? `?prefix=${encodeURIComponent(name)}` : "";
    return async () => {
      const response = await axios.get<{ obitNames: string[] }>(
        `/api/obits/names${prefixString}`
      );
      return response.data.obitNames;
    };
  }, [name]);

  useEffect(() => {
    searchObits()
      .then((obitNames) => {
        let obitNamesTitleCase: string[] = [];
        obitNames.map((name) => obitNamesTitleCase.push(toTitleCase(name)));
        setObituaryOptions(obitNamesTitleCase);
      })
      .catch((err) => {
        console.error(`Could not get obituary names list: ${err}`);
        if (err.response) console.log(err.response.data);
      });
  }, [name, searchObits]);

  const inputDidChange = (evt: ChangeEvent<{}>, newValue: string | null) => {
    setName(newValue ?? "");
  };

  const valueDidChange = (evt: ChangeEvent<{}>, newValue: string | null) => {
    setName(newValue ?? "");
  };

  function toTitleCase(str: string) {
    return str.replace(/\w\S*/g, function (txt) {
      return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
    });
  }

  return (
    <>
      <Helmet>
        <title>All Obituaries</title>
      </Helmet>
      <>
        <Grid container>
          <Grid item xs={9}>
            <Typography className={classes.obituariesTitle}>
              Obituaries
            </Typography>
          </Grid>
          <Grid item xs={3}>
            <Box display="flex" justifyContent="flex-end">
              <FormControl>
                <Autocomplete
                  style={{ width: "100%" }}
                  freeSolo
                  autoComplete
                  includeInputInList
                  value={name}
                  //onChange is fired when an option is selected
                  onChange={valueDidChange}
                  //onInputChange is fired when the user types
                  onInputChange={inputDidChange}
                  options={obituaryOptions ?? []}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Name Filter"
                      style={{ width: "300px" }}
                    />
                  )}
                />
              </FormControl>
            </Box>
          </Grid>
        </Grid>
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
                  <TableCell
                    component="th"
                    scope="row"
                    className={classes.title_case_text}
                  >
                    {project.isObitProject}
                  </TableCell>
                  <TableCell>{project.title}</TableCell>
                  <TableCell>
                    <CommissionEntryView entryId={project.commissionId} />
                  </TableCell>
                  <TableCell>
                    <span className="datetime">
                      {moment(project.created).format("DD/MM/YYYY HH:mm")}
                    </span>
                  </TableCell>
                  <TableCell>
                    <ProjectTypeDisplay projectTypeId={project.projectTypeId} />
                  </TableCell>
                  <TableCell>{project.user}</TableCell>
                  <TableCell align="right">
                    <Link to={"/project/" + project.id}>
                      Edit obituary project
                    </Link>
                  </TableCell>
                  <TableCell>
                    <Button
                      className={classes.openProjectButton}
                      variant="contained"
                      color="primary"
                      onClick={async () => {
                        try {
                          await openProject(project.id);
                        } catch (error) {
                          SystemNotification.open(
                            SystemNotifcationKind.Error,
                            `An error occurred when attempting to open the project.`
                          );
                          console.error(error);
                        }
                        try {
                          await updateProjectOpenedStatus(project.id);
                        } catch (error) {
                          console.error(error);
                        }
                      }}
                    >
                      Open project
                    </Button>
                    <AssetFolderLink projectId={project.id} />
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
    </>
  );
};

export default ObituariesList;
