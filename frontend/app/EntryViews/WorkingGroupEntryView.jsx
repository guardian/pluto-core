import GenericEntryView from "./GenericEntryView.jsx";
import PropTypes from "prop-types";
import React from "react";

class WorkingGroupEntryView extends GenericEntryView {
  static propTypes = {
    entryId: PropTypes.number.isRequired,
  };

  constructor(props) {
    super(props);
    this.endpoint = "/api/pluto/workinggroup";
  }

  render() {
    if (this.state.content)
      return (
        <span>
          {this.state.content.name}{" "}
          {this.state.content.hide ? "(old working group)" : ""}
        </span>
      );
    else
      return (
        <span>
          <i>(none)</i>
        </span>
      );
  }
}

export default WorkingGroupEntryView;
