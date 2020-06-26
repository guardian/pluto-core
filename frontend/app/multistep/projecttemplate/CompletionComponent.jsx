import React from "react";
import PropTypes from "prop-types";
import axios from "axios";
import SummaryComponent from "./SummaryComponent.jsx";
import ErrorViewComponent from "../common/ErrorViewComponent.jsx";
import { Redirect } from "react-router-dom";

class TemplateCompletionComponent extends React.Component {
  static propTypes = {
    currentEntry: PropTypes.number.isRequired,
    fileId: PropTypes.number.isRequired,
    projectType: PropTypes.number.isRequired,
    plutoSubtype: PropTypes.number,
    name: PropTypes.string.isRequired,
    deprecated: PropTypes.bool.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      inProgress: false,
      error: null,
      completed: true,
    };
    this.confirmClicked = this.confirmClicked.bind(this);
  }

  confirmClicked(event) {
    this.setState({ inProgress: true });
    const restUrl = this.props.currentEntry
      ? "/api/template/" + this.props.currentEntry
      : "/api/template";

    axios
      .put(restUrl, this.requestContent())
      .then((response) => {
        this.setState({ inProgress: false, completed: true });
      })
      .catch((error) => {
        this.setState({ inProgress: false, error: error });
        console.error(error);
      });
  }

  requestContent() {
    /* returns an object of keys/values to send to the server for saving */
    let rtn = {
      name: this.props.name,
      projectTypeId: this.props.projectType,
      fileRef: this.props.fileId,
      deprecated: this.props.deprecated,
    };
    if (this.props.plutoSubtype !== "" && this.props.plutoSubtype !== null)
      rtn["plutoSubtype"] = this.props.plutoSubtype;
    return rtn;
  }

  render() {
    if (this.state.completed) return <Redirect to="/template/" />;
    return (
      <div>
        <h3>Set up project template</h3>
        <p className="information">
          We will set up a new project template definition with the information
          below.
        </p>
        <SummaryComponent
          fileId={this.props.fileId}
          projectType={this.props.projectType}
          name={this.props.name}
          plutoSubtype={this.props.plutoSubtype}
          deprecated={this.props.deprecated}
        />
        <p className="information">
          Press "Confirm" to go ahead, or press Previous if you need to amend
          any details.
        </p>
        <ErrorViewComponent error={this.state.error} />
        <span style={{ float: "right" }}>
          <button onClick={this.confirmClicked}>Confirm</button>
        </span>
      </div>
    );
  }
}

export default TemplateCompletionComponent;
