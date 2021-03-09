import React from "react";
import axios from "axios";
import { Link } from "react-router-dom";
import moment from "moment";
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

class GeneralListComponent extends React.Component {
  static ITEM_LIMIT = 50;

  constructor(props) {
    super(props);
    this.state = {
      data: [],
      hovered: false,
      filterTerms: {
        match: "W_ENDSWITH",
      },
      currentPage: 0,
      maximumItemsLoaded: false,
      plutoConfig: {},
      uid: "",
      isAdmin: false,
    };

    this.pageSize = 20;

    this.gotDataCallback = this.gotDataCallback.bind(this);
    this.newElementCallback = this.newElementCallback.bind(this);
    this.filterDidUpdate = this.filterDidUpdate.bind(this);

    /* this must be supplied by a subclass */
    this.endpoint = "/unknown";
    this.filterEndpoint = null;

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

    this.canCreateNew = true;
    /* this must be supplied by a subclass */
    this.columns = [];
  }

  componentDidMount() {
    this.loadDependencies().then(() => {
      this.dependenciesDidLoad();
      this.reload();
    });
    console.log("columns: " + this.columns);
    console.log("column 1: " + this.columns[0]);
    console.log("column 2: " + this.columns[1]);
  }

  /**
   * override this in a subclass to update state once dependencies have loaded
   */
  dependenciesDidLoad() {}

  loadDependencies() {
    return new Promise((accept, reject) =>
      axios
        .get("/api/isLoggedIn")
        .then((response) => {
          if (response.data.status === "ok")
            this.setState(
              { isAdmin: response.data.isAdmin, uid: response.data.uid },
              () => accept()
            );
        })
        .catch((error) => {
          if (
            this.props.error.response &&
            this.props.error.response.status === 403
          )
            this.setState({ isAdmin: false }, () => accept());
          else {
            console.error(error);
            this.setState({ isAdmin: false, error: error }, () => reject());
          }
        })
    );
  }

  /* this method supplies a column definition as a convenience for subclasses */
  static standardColumn(name, key) {
    return {
      header: name,
      key: key,
      headerProps: { className: "dashboardheader" },
      render: (value) =>
        <span style={{ fontStyle: "italic" }}>n/a</span> ? (
          value
        ) : (
          value && value.length > 0
        ),
    };
  }

  static boolColumn(name, key) {
    return {
      header: name,
      key: key,
      headerProps: { className: "dashboardheader" },
      render: (value) => <span>{String(value)}</span>,
    };
  }

  /* this method supplies a column definition for datetimes */
  static dateTimeColumn(name, key) {
    return {
      header: name,
      key: key,
      headerProps: { className: "dashboardheader" },
      render: (value) => (
        <span className="datetime">
          {moment(value).format("ddd Do MMM, HH:mm")}
        </span>
      ),
    };
  }

  breakdownPathComponents() {
    return this.props.location.pathname.split("/");
  }

  /* this method supplies the edit and delete icons. Can't be static as <Link> relies on the object context to access
   * history. */
  actionIcons() {
    const deploymentRoot = deploymentRootPath ?? "/";

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
            <img
              className="smallicon"
              src={deploymentRoot + "assets/images/edit.png"}
              alt="Edit"
            />
          </Link>
          <Link to={"/" + componentName + "/" + id + "/delete"}>
            <img
              className="smallicon"
              src={deploymentRoot + "assets/images/delete.png"}
              alt="Delete"
            />
          </Link>
        </span>
      ),
    };
  }

  /* loads the next page of data */
  getNextPage() {
    const startAt = this.state.currentPage * this.pageSize;
    const length = this.pageSize;

    const axiosFuture = this.filterEndpoint
      ? axios.put(
          this.filterEndpoint + "?startAt=" + startAt + "&length=" + length,
          this.state.filterTerms
        )
      : axios.get(this.endpoint + "?startAt=" + startAt + "&length=" + length);

    axiosFuture
      .then((response) => {
        this.setState(
          {
            currentPage: this.state.currentPage + 1,
          },
          () => {
            this.gotDataCallback(response, () => {
              if (response.data.result.length > 0)
                if (
                  this.pageSize * this.state.currentPage >=
                  GeneralListComponent.ITEM_LIMIT
                )
                  this.setState({ maximumItemsLoaded: true });
                else this.getNextPage();
            });
          }
        );
      })
      .catch((error) => {
        console.error(error);
      });
  }

  /* reloads the data for the component based on the endpoint configured in the constructor */
  reload() {
    this.setState(
      {
        currentPage: 0,
        data: [],
      },
      () => this.getNextPage()
    );
  }

  /* called when we receive data; can be over-ridden by a subclass to do something more clever */
  gotDataCallback(response, cb) {
    this.setState(
      {
        data: this.state.data.concat(response.data.result),
      },
      cb
    );
  }

  /* called when the New button is clicked; can be over-ridden by a subclass to do something more clever */
  newElementCallback(event) {}

  /* called to insert a filtering component; should be over-ridden by a subclass if filtering is required */
  getFilterComponent() {
    return <span />;
  }

  /* this can be referenced from a filter component in a subclass and should be called to update the active filtering.
    this will cause a reload of data from the server
     */
  filterDidUpdate(newterms) {
    this.setState({ filterTerms: newterms }, () => this.reload());
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

  render() {
    return (
      <>
        <Helmet>
          <title>Core Admin</title>
        </Helmet>
        <div>
          <span className="list-title">
            <h2 className="list-title">{this.props.title}</h2>
          </span>
          {this.getFilterComponent()}
          {this.itemLimitWarning()}

          <span className="banner-control">
            <button id="newElementButton" onClick={this.newElementCallback}>
              New
            </button>
          </span>
          <EnhancedTable
            columnData={this.columns}
            tableData={this.state.data}
          />
        </div>
      </>
    );
  }
}

export default GeneralListComponent;
