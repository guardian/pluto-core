import React from "react";
import PropTypes from "prop-types";
import ErrorViewComponent from "../common/ErrorViewComponent.jsx";
import SummaryComponent from "./SummaryComponent.jsx";
import axios from "axios";
import moment from "moment";
import { Redirect } from "react-router-dom";

class CommissionCompletionComponent extends React.Component {
  static propTypes = {
    workingGroupId: PropTypes.number,
    wgList: PropTypes.array,
    title: PropTypes.string,
    productionOffice: PropTypes.string,
    scheduledCompletion: PropTypes.string,
    userName: PropTypes.string,
  };

  constructor(props) {
    super(props);

    this.state = {
      error: null,
      inProgress: false,
      createTime: moment().format("YYYY-MM-DD[T]HH:mm:ss.SSSZ"),
      completed: false,
    };
    this.confirmClicked = this.confirmClicked.bind(this);
    this.requestContent = this.requestContent.bind(this);
  }

  requestContent() {
    return {
      title: this.props.title,
      status: "New",
      workingGroupId: this.props.workingGroupId,
      created: this.state.createTime,
      updated: this.state.createTime,
      productionOffice: this.props.productionOffice,
      scheduledCompletion: moment(this.props.scheduledCompletion).format(
        "YYYY-MM-DD[T]HH:mm:ss.SSSZ"
      ),
      owner: this.props.userName,
    };
  }

  makeRequest() {
    return axios
      .request({
        method: "POST",
        url: "/api/prexit/commission",
        data: this.requestContent(),
      })
      .then(() => {
        this.setState({ inProgress: false, completed: true });
      })
      .catch((err) => {
        console.error(err);
        //see https://gist.github.com/fgilio/230ccd514e9381fafa51608fcf137253
        if (err.response) {
          if (err.response.status === 409) {
            return this.makeRequest(); //recurse with a different collection id
          }
        } else {
          this.setState({ inProgress: false, error: err });
        }
      });
  }

  confirmClicked(evt) {
    this.setState({ inProgress: true }, () => this.makeRequest());
  }

  render() {
    if (this.state.completed) return <Redirect to="/commission/" />;
    return (
      <div>
        <h3>Create new commission</h3>
        <p className="information">
          We will create a new commission with the information below.
        </p>
        <p className="information">
          Press "Confirm" to go ahead, or press Previous if you need to amend
          any details.
        </p>

        <ErrorViewComponent error={this.state.error} />

        <SummaryComponent
          commissionName={this.props.title}
          wgList={this.props.wgList}
          selectedWorkingGroupId={this.props.workingGroupId}
          createTime={this.state.createTime}
          scheduledCompletion={this.props.scheduledCompletion}
          productionOffice={this.props.productionOffice}
          userName={this.props.userName}
        />
        <span style={{ float: "right" }}>
          <button
            onClick={this.confirmClicked}
            disabled={this.state.inProgress}
            style={{ color: this.state.inProgress ? "lightgrey" : "black" }}
          >
            Confirm
          </button>
        </span>
      </div>
    );
  }
}

export default CommissionCompletionComponent;
