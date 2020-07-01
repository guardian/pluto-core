import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";
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
  Snackbar,
  SnackbarContent,
  TablePagination,
} from "@material-ui/core";
import { getWorkingGroupsOnPage } from "./helpers";

const tableHeaderTitles = ["Name", "Commissioner"];
const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
    "& .MuiTableRow-hover": {
      cursor: "pointer",
    },
  },
  createNewWorkingGroup: {
    marginBottom: "0.625rem",
  },
});
const pageSizeOptions = [25, 50, 100];

interface WorkingGroupsStateTypes {
  success?: boolean;
  name?: string;
}

type WorkingGroupsProps = RouteComponentProps<{}, {}, WorkingGroupsStateTypes>;

const WorkingGroups: React.FC<WorkingGroupsProps> = (props) => {
  const classes = useStyles();

  const [workingGroups, setWorkingGroups] = useState<WorkingGroup[]>([]);
  const [success, setSuccess] = useState<boolean | undefined>(false);
  const [name, setName] = useState<string | undefined>("");
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);

  useEffect(() => {
    if (props.location.state) {
      // Get history state
      setSuccess(props.location.state?.success);
      setName(props.location.state?.name);

      // Reset history state
      props.history.replace({
        pathname: "/working-group",
        state: {},
      });
    }

    const fetchWorkingGroupsOnPage = async () => {
      const workingGroups = await getWorkingGroupsOnPage({ page, pageSize });
      setWorkingGroups(workingGroups);
    };

    fetchWorkingGroupsOnPage();
  }, [page, pageSize]);

  const onNewWorkingGroup = (): void => {
    props.history.push("/working-group/new");
  };

  const closeSnackBar = (): void => {
    setSuccess(false);
  };

  const onRowClick = (id: number): void => {
    props.history.push(`/working-group/${id}`);
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
      <Button
        className={classes.createNewWorkingGroup}
        variant="outlined"
        onClick={onNewWorkingGroup}
      >
        New
      </Button>
      <TableContainer elevation={3} component={Paper}>
        <Table className={classes.table}>
          <TableHead>
            <TableRow>
              {tableHeaderTitles.map((title) => (
                <TableCell key={title}>{title}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {workingGroups.map(({ id, name, commissioner }) => (
              <TableRow hover={true} onClick={() => onRowClick(id)} key={id}>
                <TableCell>{name}</TableCell>
                <TableCell>{commissioner}</TableCell>
              </TableRow>
            ))}
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

      <Snackbar
        open={success}
        autoHideDuration={4000}
        onClose={closeSnackBar}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <SnackbarContent
          style={{
            backgroundColor: "#4caf50",
          }}
          message={
            <span id="client-snackbar">
              Successfully created Working Group: {name}
            </span>
          }
        />
      </Snackbar>
    </>
  );
};

export default WorkingGroups;
