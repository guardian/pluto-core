import React from "react";
import PropTypes from "prop-types";
import Omit from "lodash.omit";
import { validateVsid } from "../validators/VsidValidator.jsx";
import FilterTypeSelection from "./FilterTypeSelection.jsx";
import Grid from "@material-ui/core/Grid";
import Paper from "@material-ui/core/Paper";
import axios from "axios";

class ProjectFilterComponent extends React.Component {
  static propTypes = {
    filterDidUpdate: PropTypes.func.isRequired, //this is called when the filter state should be updated. Passed a
    //key-value object of the terms.
    isAdmin: PropTypes.bool,
    filterTerms: PropTypes.object.isRequired,
  };

  constructor(props) {
    super(props);

    this.filterSpec = [
      {
        key: "title",
        label: "",
        //this is a called for every update. if it returns anything other than NULL it's considered an
        //error and displayed alongside the control
        validator: (input) => null,
      },
      {
        key: "user",
        label: "Owner",
        valuesStateKey: "distinctOwners",
      },
      {
        key: "group",
        label: "Working Group",
        valuesStateKey: "workingGroups",
      },
    ];

    this.state = {
      fieldErrors: {},
      showFilters: false,
      matchType: "W_CONTAINS",
    };

    this.switchHidden = this.switchHidden.bind(this);
  }

  updateFilters(filterKey, value, cb) {
    let newFilters = {};
    newFilters[filterKey] = value;

    this.props.filterDidUpdate(
      Object.assign(
        {},
        this.props.filterTerms,
        { match: this.state.matchType },
        newFilters
      )
    );
  }

  addFieldError(filterKey, errorDesc, cb) {
    let newFilters = {};
    newFilters[filterKey] = errorDesc;
    this.setState({ fieldErrors: newFilters }, cb);
  }

  removeFieldError(filterKey, cb) {
    this.setState({ fieldErrors: Omit(this.state.fieldErrors, filterKey) }, cb);
  }

  entryUpdated(event, filterKey) {
    const spec = this.filterSpec.filter((entry) => entry.key === filterKey);
    const newValue = event.target.value.trim();

    if (spec[0].validator) {
      const wasError = spec[0].validator(newValue);
      if (wasError) {
        this.addFieldError(filterKey, wasError);
      } else {
        this.removeFieldError(filterKey);
        this.updateFilters(
          filterKey,
          spec[0].converter ? spec[0].converter(newValue) : newValue
        );
      }
    } else {
      this.updateFilters(filterKey, newValue);
    }
  }

  switchHidden() {
    this.setState({ showFilters: !this.state.showFilters });
  }

  showFilterError(fieldName) {
    return (
      <span>
        {this.state.fieldErrors[fieldName]
          ? this.state.fieldErrors[fieldName]
          : ""}
      </span>
    );
  }

  controlFor(filterEntry) {
    const disabled = !this.props.isAdmin && filterEntry.disabledIfNotAdmin;

    if (
      filterEntry.valuesStateKey &&
      this.state.hasOwnProperty(filterEntry.valuesStateKey)
    ) {
      return (
        <select
          disabled={disabled}
          id={filterEntry.key}
          onChange={(event) => this.entryUpdated(event, filterEntry.key)}
          value={this.props.filterTerms[filterEntry.key]}
        >
          {this.state[filterEntry.valuesStateKey].map((value) => (
            <option key={value} name={value}>
              {value}
            </option>
          ))}
        </select>
      );
    } else {
      return (
        <input
          disabled={disabled}
          id={filterEntry.key}
          onChange={(event) => this.entryUpdated(event, filterEntry.key)}
          value={this.props.filterTerms[filterEntry.key]}
        />
      );
    }
  }

  componentDidMount() {
    this.setState({ distinctOwners: [] }, () => {
      axios
        .get("/api/project/distinctowners")
        .then((result) =>
          this.setState({
            distinctOwners: ["Everyone"].concat(result.data.result.sort()),
          })
        )
        .catch((error) => {
          console.error(error);
          this.setState({ error: error });
        });
    });
    this.setState({ workingGroups: [] }, () => {
      axios
        .get("/api/pluto/workinggroup")
        .then((result) => {
          const workingGroupNames = [];
          for (var i = 0; i < result.data.result; i++) {
            workingGroupNames.push(result.data.result[i].name);
          }
          this.setState({
            workingGroups: ["All"].concat(workingGroupNames.sort()),
          });
        })
        .catch((error) => {
          console.error(error);
          this.setState({ error: error });
        });
    });
  }

  render() {
    return (
      <>
        <Grid container alignContent="space-around" justify="center">
          <i className="fa fa-search-plus" style={{ marginRight: "0.5em" }} />
          {this.filterSpec.map((filterEntry) => (
            <Grid item key={filterEntry.key}>
              <label className="filter-entry-label" htmlFor={filterEntry.key}>
                {filterEntry.label}
              </label>
              <div className="filter-entry-input">
                {this.controlFor(filterEntry)}
                <br style={{ marginTop: "5px" }} />
              </div>
            </Grid>
          ))}
        </Grid>
      </>
    );
  }
}

export default ProjectFilterComponent;
