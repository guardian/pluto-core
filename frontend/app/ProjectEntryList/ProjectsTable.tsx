import React, { useEffect, useState } from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  Box,
  useTheme,
} from "@material-ui/core";
import { SortDirection, sortListByOrder } from "../utils/lists";
import CommissionEntryView from "../EntryViews/CommissionEntryView";
import moment from "moment";
import WorkingGroupEntryView from "../EntryViews/WorkingGroupEntryView";
import {
  updateProjectOpenedStatus,
  setProjectStatusToKilled,
  openProject,
  getSimpleProjectTypeData,
} from "./helpers";
import AssetFolderLink from "./AssetFolderLink";
import EditIcon from "@material-ui/icons/Edit";
import DeleteIcon from "@material-ui/icons/Delete";
import {
  SystemNotification,
  SystemNotifcationKind,
} from "@guardian/pluto-headers";
import { useGuardianStyles } from "~/misc/utils";

const tableHeaderTitles: HeaderTitle<Project>[] = [
  { label: "Project title", key: "title" },
  { label: "Commission title", key: "commissionId" },
  { label: "Created", key: "created" },
  { label: "Group", key: "workingGroupId" },
  { label: "Status", key: "status" },
  { label: "Owner", key: "user" },
  { label: "" },
  { label: "" },
  { label: "Open" },
  { label: "" },
];

declare var deploymentRootPath: string;

const ActionIcons: React.FC<{ id: number; isAdmin?: boolean }> = ({
  id,
  isAdmin = false,
}) => (
  <IconButton
    onClick={(event) =>
      window.open(`${deploymentRootPath}project/${id}`, "_blank")
    }
  >
    <EditIcon />
  </IconButton>
);

interface ProjectsTableProps {
  //CSS class to style the table
  className: string;
  //array of page sizes to present to the user
  pageSizeOptions: number[];
  //callback to tell the parent to update the source data
  updateRequired: (page: number, pageSize: number) => void;
  //list of projects to display
  projects: Project[];
  //is the user an admin
  isAdmin?: boolean;
  projectCount: number;
}

const ProjectsTable: React.FC<ProjectsTableProps> = (props) => {
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(
    props.pageSizeOptions[0]
  );
  const [refreshGeneration, setRefreshGeneration] = useState<number>(0);

  const [orderBy, setOrderBy] = useState<keyof Project>("created");
  const [order, setOrder] = useState<SortDirection>("desc");

  const classes = useGuardianStyles();
  const [openDialog, setOpenDialog] = useState<boolean>(false);
  const [updatingProject, setUpdatingProject] = useState<number>(0);
  const [projectTypeData, setProjectTypeData] = useState<any>({});

  useEffect(() => {
    console.log("filter terms or search changed, updating...");
    props.updateRequired(page, rowsPerPage);
  }, [page, rowsPerPage, order, orderBy, refreshGeneration]);

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

  const closeDialog = () => {
    setOpenDialog(false);
  };

  const onDeleteProject = async () => {
    closeDialog();

    try {
      const projectId = updatingProject as number;
      await setProjectStatusToKilled(projectId);

      setRefreshGeneration(refreshGeneration + 1);

      SystemNotification.open(
        SystemNotifcationKind.Success,
        `Successfully killed project: "${updatingProject}"`
      );
    } catch {
      SystemNotification.open(
        SystemNotifcationKind.Error,
        `Failed to kill project "${updatingProject}"`
      );
    }
  };

  const customCellStyle = { width: "200px" };

  useEffect(() => {
    const fetchProjectTypeData = async () => {
      try {
        const projectTypeData = await getSimpleProjectTypeData();
        console.log(projectTypeData);
        setProjectTypeData(projectTypeData);
      } catch (error) {
        console.error("Could get project type data:", error);
      }
    };

    fetchProjectTypeData();
  }, []);

  const imagePath = (imageName: string) => {
    return "/pluto-core/assets/images/types/" + imageName + ".png";
  };

  const detectDarkTheme = () => {
    const isDarkTheme = useTheme().palette.type === "dark";
    return isDarkTheme;
  };

  const backgroundColourForType = (typeName: string) => {
    const darkColours: any = {
      Cubase: "#502d2c",
      "After Effects": "#613950",
      Premiere: "#2a2a57",
      Prelude: "#5e382c",
      Audition: "#2d533d",
      Migrated: "#414141",
    };
    const lightColours: any = {
      Cubase: "#ffd8e3",
      "After Effects": "#ffdef1",
      Premiere: "#d0d0ff",
      Prelude: "#ffdccf",
      Audition: "#d1ffe5",
      Migrated: "#ffffff",
    };
    if (detectDarkTheme()) {
      return darkColours[typeName];
    } else {
      return lightColours[typeName];
    }
  };

  return (
    <>
      <TableContainer>
        <Table className={props.className}>
          <TableHead>
            <TableRow>
              {tableHeaderTitles.map((title, index) => (
                <TableCell
                  key={title.label ? title.label : index}
                  sortDirection={order}
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
            {sortListByOrder(props.projects, orderBy, order).map((project) => {
              const {
                id,
                title,
                commissionId,
                created,
                workingGroupId,
                status,
                user: projectUser,
                projectTypeId,
              } = project;
              return (
                <TableRow
                  key={id}
                  hover
                  style={{
                    backgroundColor: backgroundColourForType(
                      projectTypeData[projectTypeId]
                    ),
                  }}
                >
                  <TableCell>{title}</TableCell>
                  <TableCell>
                    <CommissionEntryView entryId={commissionId} />
                  </TableCell>
                  <TableCell>
                    <span className="datetime">
                      {moment(created).format("DD/MM/YYYY HH:mm A")}
                    </span>
                  </TableCell>
                  <TableCell>
                    <WorkingGroupEntryView entryId={workingGroupId} />
                  </TableCell>
                  <TableCell>{status}</TableCell>
                  <TableCell>{projectUser.replace(/\|/g, " ")}</TableCell>
                  <TableCell>
                    <img src={imagePath(projectTypeData[projectTypeId])} />
                  </TableCell>
                  <TableCell>
                    <Box width="100px">
                      <span className="icons">
                        <ActionIcons id={id} isAdmin={props.isAdmin ?? false} />
                        <IconButton
                          onClick={(event) => {
                            event.stopPropagation();
                            setUpdatingProject(id);
                            setOpenDialog(true);
                          }}
                        >
                          <DeleteIcon />
                        </IconButton>
                      </span>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Button
                      className={classes.openProjectButton}
                      variant="contained"
                      color="primary"
                      onClick={async () => {
                        try {
                          await openProject(id);
                        } catch (error) {
                          SystemNotification.open(
                            SystemNotifcationKind.Error,
                            `An error occurred when attempting to open the project. `
                          );
                          console.error(error);
                        }

                        try {
                          await updateProjectOpenedStatus(id);

                          await props.updateRequired(page, rowsPerPage);
                        } catch (error) {
                          console.error(error);
                        }
                      }}
                    >
                      Open project
                    </Button>
                  </TableCell>
                  <TableCell>
                    <AssetFolderLink projectId={id} />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      <TablePagination
        rowsPerPageOptions={props.pageSizeOptions}
        component="div"
        count={props.projectCount}
        rowsPerPage={rowsPerPage}
        page={page}
        onPageChange={handleChangePage}
        onRowsPerPageChange={handleChangeRowsPerPage}
      />
      <Dialog
        open={openDialog}
        onClose={closeDialog}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title">Delete Project</DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            This marks the project and all its media as deletable. While media
            will not be removed immediately, you should not do this unless you
            are happy that the attached media can be removed. Do you want to
            continue?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Cancel</Button>
          <Button color="secondary" onClick={onDeleteProject}>
            Okay
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ProjectsTable;
