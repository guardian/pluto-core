import React from "react";
import PropTypes from "prop-types";

class StatusIndicator extends React.Component {
  static propTypes = {
    status: PropTypes.string.isRequired,
  };

  constructor(props) {
    super(props);
  }

  iconClassName() {
    switch (this.props.status) {
      case "ONLINE":
        return "check-circle";
      case "OFFLINE":
        return "chevron-circle-down";
      case "DISAPPEARED":
        return "asterisk";
      case "MISCONFIGURED":
        return "fa-cogs";
      case "UNKNOWN":
        return "question";
      default:
        return "question";
    }
  }

  iconColour() {
    switch (this.props.status) {
      case "ONLINE":
        return "green";
      case "OFFLINE":
        return "orange";
      case "DISAPPEARED":
        return "red";
      case "MISCONFIGURED":
        return "orange";
      case "UNKNOWN":
        return "blue";
      default:
        return "blue";
    }
  }

  iconMarkup() {
    return (
      <span
        className={"fa fa-" + this.iconClassName()}
        style={{ color: this.iconColour() }}
        alt={this.props.status}
      />
    );
  }

  render() {
    if (this.props.showLabel) {
      return (
        <span>
          {this.iconMarkup()}
          <span className="storage-status">{this.props.status}</span>
        </span>
      );
    } else {
      return this.iconMarkup();
    }
  }
}

export default StatusIndicator;
