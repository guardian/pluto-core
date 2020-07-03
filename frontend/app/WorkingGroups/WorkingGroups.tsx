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
  TableSortLabel,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from "@material-ui/core";
import DeleteIcon from "@material-ui/icons/Delete";
import { getWorkingGroupsOnPage, deleteWorkingGroup } from "./helpers";
import { sortListByOrder, Order } from "../utils/utils";
import { isLoggedIn } from "../utils/api";

interface HeaderTitles {
  label: string;
  key?: keyof WorkingGroup;
}

const tableHeaderTitles: HeaderTitles[] = [
  { label: "Name", key: "name" },
  { label: "Commissioner", key: "commissioner" },
  { label: "" },
];
const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
    "& .MuiTableRow-hover": {
      cursor: "pointer",
    },
  },
  createNewWorkingGroup: {
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

interface WorkingGroupsStateTypes {
  success?: boolean;
  editing?: boolean;
  workingGroup?: WorkingGroup;
}

type WorkingGroupsProps = RouteComponentProps<{}, {}, WorkingGroupsStateTypes>;

const WorkingGroups: React.FC<WorkingGroupsProps> = (props) => {
  const classes = useStyles();

  const [workingGroups, setWorkingGroups] = useState<WorkingGroup[]>([]);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [success, setSuccess] = useState<boolean | undefined>(false);
  const [failedToDelete, setFailedToDelete] = useState<boolean | undefined>(
    false
  );
  const [updatingWorkingGroup, setUpdatingWorkingGroup] = useState<
    WorkingGroup | undefined
  >(undefined);
  const [isEditing, setIsEditing] = useState<boolean | undefined>(false);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);
  const [openDialog, setOpenDialog] = useState<boolean>(false);
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const [order, setOrder] = useState<Order>("asc");
  const [orderBy, setOrderBy] = useState<keyof WorkingGroup>("name");

  useEffect(() => {
    if (props.location.state) {
      // Get history state
      setSuccess(props.location.state?.success);
      setUpdatingWorkingGroup(props.location.state?.workingGroup);
      setIsEditing(props.location.state?.editing);

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

    const fetchWhoIsLoggedIn = async () => {
      try {
        const loggedIn = await isLoggedIn();
        setIsAdmin(loggedIn.isAdmin);
      } catch {
        setIsAdmin(false);
      }
    };

    fetchWhoIsLoggedIn();

    fetchWorkingGroupsOnPage();
  }, [page, pageSize]);

  const onNewWorkingGroup = (): void => {
    props.history.push("/working-group/new");
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

  const sortByColumn = (property: keyof WorkingGroup) => (
    _event: React.MouseEvent<unknown>
  ) => {
    const isAsc = orderBy === property && order === "asc";
    setOrder(isAsc ? "desc" : "asc");
    setOrderBy(property);
  };

  const closeDialog = () => {
    setOpenDialog(false);
  };

  const onDeleteWorkingGroup = async () => {
    closeDialog();

    try {
      const workingGroupId = updatingWorkingGroup?.id as number;
      await deleteWorkingGroup(workingGroupId);
      setWorkingGroups(
        workingGroups.filter((group) => group.id !== workingGroupId)
      );

      setSuccess(true);
    } catch {
      setFailedToDelete(true);
    }
  };

  const closeSnackBarSuccess = (): void => {
    setSuccess(false);
    setIsEditing(false);
  };

  const closeSnackBarFailed = (): void => {
    setFailedToDelete(false);
    setIsDeleting(false);
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
              {sortListByOrder(workingGroups, order, orderBy).map(
                ({ id, name, commissioner, hide }) => (
                  <TableRow
                    hover={true}
                    onClick={() => props.history.push(`/working-group/${id}`)}
                    key={id}
                  >
                    <TableCell>{name}</TableCell>
                    <TableCell>{commissioner}</TableCell>
                    <TableCell align={"right"}>
                      {isAdmin && (
                        <IconButton
                          onClick={(event) => {
                            event.stopPropagation();
                            setUpdatingWorkingGroup({
                              id,
                              name,
                              commissioner,
                              hide,
                            });
                            setIsDeleting(true);
                            setOpenDialog(true);
                          }}
                        >
                          <DeleteIcon></DeleteIcon>
                        </IconButton>
                      )}
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

      <Dialog
        open={openDialog}
        onClose={closeDialog}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title">Delete Working Group</DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            Are you sure you want to delete Working Group "
            {updatingWorkingGroup?.name}"?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Cancel</Button>
          <Button color="secondary" onClick={onDeleteWorkingGroup}>
            Ok
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={success}
        autoHideDuration={4000}
        onClose={closeSnackBarSuccess}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <SnackbarContent
          style={{
            backgroundColor: "#4caf50",
          }}
          message={
            <span id="client-snackbar">{`Successfully ${
              isDeleting ? "deleted" : isEditing ? "updated" : "created"
            } Working Group: "${updatingWorkingGroup?.name}"`}</span>
          }
        />
      </Snackbar>
      <Snackbar
        open={failedToDelete}
        autoHideDuration={4000}
        onClose={closeSnackBarFailed}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <SnackbarContent
          style={{
            backgroundColor: "#f44336",
          }}
          message={
            <span id="client-snackbar">
              {`Failed to delete Working Group "${updatingWorkingGroup?.name}"`}
            </span>
          }
        />
      </Snackbar>
    </>
  );
};

export default WorkingGroups;
