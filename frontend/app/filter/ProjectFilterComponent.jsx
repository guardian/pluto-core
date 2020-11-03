import React from "react";
import PropTypes from "prop-types";
import Omit from "lodash.omit";
import { validateVsid } from "../validators/VsidValidator.jsx";
import FilterTypeSelection from "./FilterTypeSelection.jsx";
import { Grid, Select, MenuItem } from "@material-ui/core";
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
        intValues: "workingGroupsIds",
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
    const newValue = event.target.value;

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
      if (
        filterEntry.intValues &&
        this.state.hasOwnProperty(filterEntry.intValues)
      ) {
        var groupsObject = [];

        for (
          var i = 0;
          i < this.state[filterEntry.valuesStateKey].length;
          i++
        ) {
          groupsObject.push(
            <MenuItem
              key={this.state[filterEntry.intValues][i]}
              name={this.state[filterEntry.intValues][i]}
              value={this.state[filterEntry.intValues][i].toString()}
            >
              {this.state[filterEntry.valuesStateKey][i]}
            </MenuItem>
          );
        }
        return (
          <Select
            id={filterEntry.key}
            onChange={(event) => this.entryUpdated(event, filterEntry.key)}
            value={
              this.props.filterTerms[filterEntry.key]
                ? this.props.filterTerms[filterEntry.key]
                : "All"
            }
          >
            {groupsObject}
          </Select>
        );
      } else {
        console.log(
          "filterEntry.key: " +
            filterEntry.key +
            " this.props.filterTerms[filterEntry.key]: " +
            this.props.filterTerms[filterEntry.key]
        );
        return (
          <Select
            id={filterEntry.key}
            onChange={(event) => this.entryUpdated(event, filterEntry.key)}
            value={
              this.props.filterTerms[filterEntry.key]
                ? this.props.filterTerms[filterEntry.key]
                : "Everyone"
            }
          >
            {this.state[filterEntry.valuesStateKey].map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </Select>
        );
      }
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
            distinctOwners: ["Everyone", "Mine"].concat(
              result.data.result.sort()
            ),
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
          var workingGroupNames = [];
          var workingGroupIds = [];
          console.log(result.data.result);
          for (var i = 0; i < result.data.result.length; i++) {
            console.log(result.data.result[i].name);
            workingGroupNames.push(result.data.result[i].name);
            workingGroupIds.push(result.data.result[i].id);
          }
          console.log(workingGroupNames);
          this.setState({
            workingGroups: ["All"].concat(workingGroupNames.sort()),
            workingGroupsIds: ["All"].concat(workingGroupIds),
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
        <Grid
          container
          alignContent="center"
          justify="left"
          alignItems="center"
        >
          {this.filterSpec.map((filterEntry) => (
            <Grid item key={filterEntry.key}>
              <label
                className="project-filter-entry-label"
                htmlFor={filterEntry.key}
              >
                {filterEntry.key == "title" ? (
                  <i className="fa fa-search" />
                ) : null}
                {filterEntry.label}
              </label>
              <div className="project-filter-entry-input">
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
