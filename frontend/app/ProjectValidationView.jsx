import React from "react";
import axios from "axios";
import SortableTable from "react-sortable-table";
import GeneralListComponent from "./GeneralListComponent.jsx";
import ProjectTypeView from "./EntryViews/ProjectTypeView.jsx";
import WorkingGroupEntryView from "./EntryViews/WorkingGroupEntryView.jsx";
import CommissionEntryView from "./EntryViews/CommissionEntryView.jsx";
import { Link } from "react-router-dom";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx";
import { Helmet } from "react-helmet";
import {
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
  FormControlLabel,
  Paper,
  Switch,
} from "@material-ui/core";
import PropTypes from "prop-types";

function descendingComparator(a, b, orderBy) {
  if (b[orderBy] < a[orderBy]) {
    return -1;
  }
  if (b[orderBy] > a[orderBy]) {
    return 1;
  }
  return 0;
}

function getComparator(order, orderBy) {
  return order === "desc"
    ? (a, b) => descendingComparator(a, b, orderBy)
    : (a, b) => -descendingComparator(a, b, orderBy);
}

function stableSort(array, comparator) {
  const stabilizedThis = array.map((el, index) => [el, index]);
  stabilizedThis.sort((a, b) => {
    const order = comparator(a[0], b[0]);
    if (order !== 0) return order;
    return a[1] - b[1];
  });
  return stabilizedThis.map((el) => el[0]);
}

function EnhancedTableHead(props) {
  const { classes, order, orderBy, onRequestSort, columnData } = props;
  const createSortHandler = (property) => (event) => {
    onRequestSort(event, property);
  };

  return (
    <TableHead>
      <TableRow>
        {columnData.map((headCell) => (
          <TableCell
            key={headCell.key}
            sortDirection={orderBy === headCell.key ? order : false}
          >
            <TableSortLabel
              active={orderBy === headCell.key}
              direction={orderBy === headCell.key ? order : "asc"}
              onClick={createSortHandler(headCell.key)}
            >
              {headCell.header}
              {orderBy === headCell.key ? (
                <span className={classes.visuallyHidden}>
                  {order === "desc" ? "sorted descending" : "sorted ascending"}
                </span>
              ) : null}
            </TableSortLabel>
          </TableCell>
        ))}
      </TableRow>
    </TableHead>
  );
}

EnhancedTableHead.propTypes = {
  classes: PropTypes.object.isRequired,
  onRequestSort: PropTypes.func.isRequired,
  order: PropTypes.oneOf(["asc", "desc"]).isRequired,
  orderBy: PropTypes.string.isRequired,
  rowCount: PropTypes.number.isRequired,
  columnData: PropTypes.array.isRequired,
};

const useStyles = makeStyles((theme) => ({
  root: {
    width: "100%",
  },
  paper: {
    width: "100%",
    marginBottom: theme.spacing(2),
  },
  table: {
    minWidth: 750,
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
}));

EnhancedTable.propTypes = {
  columnData: PropTypes.array.isRequired,
  tableData: PropTypes.array.isRequired,
};

export function EnhancedTable(props) {
  const classes = useStyles();
  const [order, setOrder] = React.useState("asc");
  const [orderBy, setOrderBy] = React.useState("calories");
  const [page, setPage] = React.useState(0);
  const [dense, setDense] = React.useState(false);
  const [rowsPerPage, setRowsPerPage] = React.useState(50);
  const { columnData, tableData } = props;

  const handleRequestSort = (event, property) => {
    const isAsc = orderBy === property && order === "asc";
    setOrder(isAsc ? "desc" : "asc");
    setOrderBy(property);
  };

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleChangeDense = (event) => {
    setDense(event.target.checked);
  };

  return (
    <div className={classes.root}>
      <Paper className={classes.paper}>
        <TableContainer>
          <Table
            className={classes.table}
            aria-labelledby="tableTitle"
            size={dense ? "small" : "medium"}
            aria-label="enhanced table"
          >
            <EnhancedTableHead
              classes={classes}
              order={order}
              orderBy={orderBy}
              onRequestSort={handleRequestSort}
              rowCount={tableData.length}
              columnData={columnData}
            />
            <TableBody>
              {stableSort(tableData, getComparator(order, orderBy))
                .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                .map((row, index) => {
                  return (
                    <ListTableRow key={index} data={row} columns={columnData} />
                  );
                })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          rowsPerPageOptions={[10, 25, 50]}
          component="div"
          count={tableData.length}
          rowsPerPage={rowsPerPage}
          page={page}
          onChangePage={handleChangePage}
          onChangeRowsPerPage={handleChangeRowsPerPage}
        />
      </Paper>
      <FormControlLabel
        control={<Switch checked={dense} onChange={handleChangeDense} />}
        label="Dense padding"
      />
    </div>
  );
}

class ListTableRow extends React.Component {
  render() {
    const tableRowData = this.props.columns.map(
      function (item, index) {
        let value = this.props.data[item.key];
        if (item.render) {
          value = item.render(value);
        }
        if (item.renderWithData) {
          value = item.renderWithData(this.props.data);
        }
        return (
          <TableCell
            key={index}
            style={item.dataStyle}
            {...(item.dataProps || {})}
          >
            {value}
          </TableCell>
        );
      }.bind(this)
    );

    return <TableRow>{tableRowData}</TableRow>;
  }
}

class ProjectValidationView extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loading: false,
      totalProjectCount: 0,
      problemProjects: [],
      lastError: null,
      hasRun: false,
    };

    this.columns = [
      {
        header: "Id",
        key: "id",
        defaultSorting: "desc",
        dataProps: { className: "align-right" },
        headerProps: { className: "dashboardheader" },
      },
      GeneralListComponent.standardColumn("Title", "title"),
      {
        header: "Pluto project",
        key: "vidispineId",
        render: this.getPlutoLink,
        headerProps: { className: "dashboardheader" },
      },
      {
        header: "Project type",
        key: "projectTypeId",
        render: (typeId) => <ProjectTypeView entryId={typeId} />,
        headerProps: { className: "dashboardheader" },
      },
      GeneralListComponent.dateTimeColumn("Created", "created"),
      GeneralListComponent.standardColumn("Owner", "user"),
      {
        header: "Working group",
        key: "workingGroupId",
        render: (typeId) => <WorkingGroupEntryView entryId={typeId} />,
        headerProps: { className: "dashboardheader" },
      },
      {
        header: "Commission",
        key: "commissionId",
        render: (typeId) => <CommissionEntryView entryId={typeId} />,
        headerProps: { className: "dashboardheader" },
      },
      this.actionIcons(),
      {
        header: "",
        key: "id",
        headerProps: { className: "dashboardheader" },
        render: (projid) => (
          <a target="_blank" href={"pluto:openproject:" + projid}>
            Open project
          </a>
        ),
      },
    ];

    this.style = {
      backgroundColor: "#eee",
      border: "1px solid black",
      borderCollapse: "collapse",
    };

    this.iconStyle = {
      color: "#aaa",
      paddingLeft: "5px",
      paddingRight: "5px",
    };

    this.runValidation = this.runValidation.bind(this);
  }

  breakdownPathComponents() {
    return this.props.location.pathname.split("/");
  }

  /* this method supplies the edit and delete icons. Can't be static as <Link> relies on the object context to access
   * history. */
  actionIcons() {
    const componentName = this.breakdownPathComponents()[1];
    return {
      header: "",
      key: "id",
      render: (id) => (
        <span
          className="icons"
          style={{ display: this.state.isAdmin ? "inherit" : "none" }}
        >
          <Link to={"/" + componentName + "/" + id}>
            <img className="smallicon" src="/assets/images/edit.png" />
          </Link>
          <Link to={"/" + componentName + "/" + id + "/delete"}>
            <img className="smallicon" src="/assets/images/delete.png" />
          </Link>
        </span>
      ),
    };
  }

  getFilterComponent() {
    return <p />;
  }

  runValidation() {
    this.setState({ loading: true }, () =>
      axios
        .post("/api/project/validate")
        .then((result) => {
          this.setState({
            loading: false,
            hasRun: true,
            totalProjectCount: result.data.totalProjectsCount,
            problemProjects: result.data.failedProjectsList,
          });
        })
        .catch((err) => {
          console.error(err);
          this.setState({ loading: false, hasRun: true, lastError: err });
        })
    );
  }

  itemLimitWarning() {
    if (this.state.maximumItemsLoaded)
      return (
        <p className="warning-text">
          <i
            className="fa-info fa"
            style={{ marginRight: "0.5em", color: "orange" }}
          />
          Maximum of {GeneralListComponent.ITEM_LIMIT} items have been loaded.
          Use filters to narrow this down.
        </p>
      );
    else return <p style={{ margin: 0 }} />;
  }

  showRunning() {
    if (this.state.loading) {
      return (
        <p className="warning-text">
          <img
            src="/assets/images/uploading.svg"
            className="smallicon"
            style={{
              display: this.state.loading ? "inline" : "none",
              verticalAlign: "middle",
              marginRight: "1em",
            }}
          />
          Searching for unlinked projects...
        </p>
      );
    } else {
      return <p />;
    }
  }

  showResult() {
    if (!this.state.loading && !this.state.hasRun) {
      return (
        <p style={{ marginLeft: "1em" }}>
          <i
            className="fa fa-3x fa-info-circle validation-status-text"
            style={{ color: "blue", verticalAlign: "middle" }}
          />
          This function checks that all of the projects in the database exist at
          their correct filesystem location.
          <br />
          Click "Run Validation" to perform the scan.
        </p>
      );
    }

    if (!this.state.loading && this.state.hasRun) {
      if (this.state.totalProjectCount === 0) {
        return (
          <p>
            <i
              className="fa fa-3x fa-exclamation-triangle validation-status-text"
              style={{ color: "orange", verticalAlign: "middle" }}
            />
            Hmmm, there were no projects found to scan.
          </p>
        );
      }
      if (this.state.problemProjects.length === 0) {
        return (
          <p>
            <i
              className="fa fa-3x fa-smile-o validation-status-text"
              style={{ color: "#f9e100", verticalAlign: "middle" }}
            />
            Hooray, no unlinked projects found! {this.state.totalProjectCount}{" "}
            projects checked successfully
          </p>
        );
      } else {
        return (
          <p>
            <i
              className="fa fa-3x fa-exclamation-triangle validation-status-text"
              style={{ color: "orange", verticalAlign: "middle" }}
            />
            {this.state.problemProjects.length} projects were not found on their
            correct storage locations (scanned {this.state.totalProjectCount}{" "}
            projects in total)
            <br />
            Affected projects are shown in the table below.
          </p>
        );
      }
    }
  }

  showError() {
    if (!this.state.loading && this.state.lastError) {
      return <ErrorViewComponent error={this.state.lastError} />;
    }
  }

  render() {
    return (
      <div>
        <Helmet>
          <title>Core Admin</title>
        </Helmet>
        <span className="list-title">
          <h2 className="list-title">Validate Projects</h2>
        </span>
        {this.getFilterComponent()}
        {this.itemLimitWarning()}
        {this.showRunning()}
        {this.showError()}

        <span className="banner-control">
          <button id="newElementButton" onClick={this.runValidation}>
            Run validation
          </button>
        </span>

        {this.showResult()}

        <SortableTable
          data={this.state.problemProjects}
          columns={this.columns}
          style={
            this.state.problemProjects.length > 0
              ? this.style
              : { display: "none" }
          }
          iconStyle={this.iconStyle}
          tableProps={{ className: "dashboardpanel" }}
        />
        {this.state.problemProjects.length > 0 ? (
          <EnhancedTable
            columnData={this.columns}
            tableData={this.state.problemProjects}
          />
        ) : null}
      </div>
    );
  }
}

export default ProjectValidationView;
