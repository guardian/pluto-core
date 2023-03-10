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
  Button,
  TablePagination,
  TableSortLabel,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Tooltip,
} from "@material-ui/core";
import DeleteIcon from "@material-ui/icons/Delete";
import { getWorkingGroupsOnPage, deleteWorkingGroup } from "./helpers";
import { sortListByOrder, SortDirection } from "../utils/lists";
import { isLoggedIn } from "../utils/api";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import { Helmet } from "react-helmet";
import { Visibility, VisibilityOff } from "@material-ui/icons";
import { useGuardianStyles } from "~/misc/utils";

const tableHeaderTitles: HeaderTitle<WorkingGroup>[] = [
  { label: "Name", key: "name" },
  { label: "Commissioner", key: "commissioner" },
  { label: "Visibility", key: "hide" },
  { label: "" },
];

const pageSizeOptions = [25, 50, 100];

const WorkingGroups: React.FC<RouteComponentProps> = (props) => {
  const classes = useGuardianStyles();

  const [workingGroups, setWorkingGroups] = useState<WorkingGroup[]>([]);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [updatingWorkingGroup, setUpdatingWorkingGroup] = useState<
    WorkingGroup | undefined
  >(undefined);
  const [openDialog, setOpenDialog] = useState<boolean>(false);
  const [page, setPage] = useState(0);
  const [pageSize, setRowsPerPage] = useState(pageSizeOptions[0]);
  const [order, setOrder] = useState<SortDirection>("asc");
  const [orderBy, setOrderBy] = useState<keyof WorkingGroup>("name");

  useEffect(() => {
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

      SystemNotification.open(
        SystemNotifcationKind.Success,
        `Successfully deleted
        Working Group: "${updatingWorkingGroup?.name}"`
      );
    } catch {
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Failed to delete Working Group "${updatingWorkingGroup?.name}"`
      );
    }
  };

  return (
    <>
      <Helmet>
        <title>Working Groups - Pluto Admin</title>
      </Helmet>
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
              {sortListByOrder(workingGroups, orderBy, order).map(
                ({ id, name, commissioner, hide }) => (
                  <TableRow
                    hover={true}
                    onClick={() => props.history.push(`/working-group/${id}`)}
                    key={id}
                  >
                    <TableCell>{name}</TableCell>
                    <TableCell>{commissioner}</TableCell>
                    <TableCell>
                      {hide ? (
                        <Tooltip title="This working group is discontinued">
                          <VisibilityOff className={classes.visibilityIcon} />
                        </Tooltip>
                      ) : (
                        <Tooltip title="This working group is not discontinued">
                          <Visibility className={classes.visibilityIcon} />
                        </Tooltip>
                      )}
                    </TableCell>
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
                            setOpenDialog(true);
                          }}
                        >
                          <DeleteIcon />
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
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
          // FIXME: remove when count is correct
          labelDisplayedRows={({ from, to }) => `${from}-${to}`}
        />
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
    </>
  );
};

export default WorkingGroups;
