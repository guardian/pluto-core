import React from "react";
import PropTypes from "prop-types";
import { Grid, Select, MenuItem, Checkbox } from "@material-ui/core";
import axios from "axios";

class ProjectFilterComponent extends React.Component {
  static propTypes = {
    filterDidUpdate: PropTypes.func.isRequired, //this is called when the filter state should be updated. Passed a
    //key-value object of the terms.
    isAdmin: PropTypes.bool,
    filterTerms: PropTypes.object.isRequired,
    isProject: PropTypes.bool,
  };

  constructor(props) {
    super(props);

    this.filterSpec = [
      {
        key: "title",
        label: "",
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
      {
        key: "showKilled",
        label: "Show Killed",
      },
    ];

    this.state = {
      fieldErrors: {},
      showFilters: false,
      matchType: "W_CONTAINS",
    };
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

  entryUpdated(event, filterKey) {
    const spec = this.filterSpec.filter((entry) => entry.key === filterKey);
    const newValue = event.target.value;

    if (filterKey == "showKilled") {
      this.updateFilters(filterKey, event.target.checked);
    } else if (spec[0].validator) {
      const wasError = spec[0].validator(newValue);
      if (wasError) {
        console.error("Error setting filter key.", filterKey, wasError);
      } else {
        this.updateFilters(
          filterKey,
          spec[0].converter ? spec[0].converter(newValue) : newValue
        );
      }
    } else {
      this.updateFilters(filterKey, newValue);
    }
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
        let groupsObject = [];

        for (
          let i = 0;
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
    } else if (filterEntry.key == "showKilled") {
      if (this.props.isProject) {
        return (
          <Checkbox
            id={filterEntry.key}
            onChange={(event) => this.entryUpdated(event, filterEntry.key)}
          />
        );
      } else {
        return null;
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
        .get("/api/pluto/workinggroup?length=999999")
        .then((result) => {
          const sortedGroups = result.data.result.sort((a, b) =>
            a.name.localeCompare(b.name)
          );
          var workingGroupNames = [];
          var workingGroupIds = [];
          for (var i = 0; i < sortedGroups.length; i++) {
            workingGroupNames.push(sortedGroups[i].name);
            workingGroupIds.push(sortedGroups[i].id);
          }
          this.setState({
            workingGroups: ["All"].concat(workingGroupNames),
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
                className={
                  filterEntry.key == "showKilled"
                    ? "project-filter-entry-label-showkilled"
                    : "project-filter-entry-label"
                }
                htmlFor={filterEntry.key}
              >
                {filterEntry.key == "title" ? (
                  <i className="fa fa-search" />
                ) : null}
                {filterEntry.key == "showKilled" &&
                this.props.isProject == false
                  ? null
                  : filterEntry.label}
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
