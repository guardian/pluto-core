import React from "react";
import PropTypes from "prop-types";
import WorkingGroupEntryView from "../../EntryViews/WorkingGroupEntryView.jsx";
import moment from "moment";

class SummaryComponent extends React.Component {
  static propTypes = {
    commissionName: PropTypes.string.isRequired,
    wgList: PropTypes.array.isRequired,
    selectedWorkingGroupId: PropTypes.number.isRequired,
    createTime: PropTypes.string.isRequired,
    productionOffice: PropTypes.string.isRequired,
    scheduledCompletion: PropTypes.string.isRequired,
    userName: PropTypes.string.isRequired,
  };

  constructor(props) {
    super(props);
  }

  render() {
    const completionTime = moment(this.props.scheduledCompletion);
    return (
      <table>
        <tbody>
          <tr>
            <td>New commission name</td>
            <td id="project-name">{this.props.commissionName}</td>
            <td id="project-name-warning">
              {this.props.commissionName === "" ? (
                <span className="error-text">
                  You must specify a name for your new commission
                </span>
              ) : (
                <span />
              )}
            </td>
          </tr>
          <tr>
            <td>Working group</td>
            <td id="working-group">
              <WorkingGroupEntryView
                entryId={this.props.selectedWorkingGroupId}
              />
            </td>
            <td id="working-group-warning">
              {this.props.selectedWorkingGroupId === null ? (
                <span className="error-text">
                  Your commission must belong to a working group
                </span>
              ) : (
                <span />
              )}
            </td>
          </tr>
          <tr>
            <td>Created by</td>
            <td id="creator-username">
              <i className="fa fa-user" />
              {this.props.userName}
            </td>
          </tr>
          <tr>
            <td>Creation time</td>
            <td id="create-time">
              {moment(this.props.createTime).format("HH:MM on ddd Do MMM YYYY")}
            </td>
          </tr>
          <tr>
            <td>Production Office</td>
            <td id="production-office">{this.props.productionOffice}</td>
          </tr>
          <tr>
            <td>Scheduled Completion</td>
            <td id="scheduled-completion">
              {completionTime.format("ddd Do MMM YYYY")} (
              {completionTime.fromNow()})
            </td>
            <td id="scheduled-completion-warning">
              {completionTime.subtract(1, "years").isAfter(moment()) ? (
                <span className="error-text">
                  This is very far in the future, are you sure that is right?
                </span>
              ) : (
                <span />
              )}
            </td>
          </tr>
        </tbody>
      </table>
    );
  }
}

export default SummaryComponent;
