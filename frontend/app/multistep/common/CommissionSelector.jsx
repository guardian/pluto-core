import React from "react";
import PropTypes from "prop-types";
import FilterableList from "../../common/FilterableList.jsx";

class CommissionSelector extends React.Component {
  static propTypes = {
    workingGroupId: PropTypes.number,
    selectedCommissionId: PropTypes.number,
    showStatus: PropTypes.string,
    valueWasSet: PropTypes.func.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      refreshCounter: 0,
    };

    this.makeSearchDoc = this.makeSearchDoc.bind(this);
  }

  componentDidUpdate(prevProps) {
    if (
      prevProps.workingGroupId !== this.props.workingGroupId ||
      prevProps.showStatus !== this.props.showStatus
    )
      this.setState({ refreshCounter: this.state.refreshCounter + 1 });
  }

  static convertContent(contentList) {
    return contentList.result.map((comm) => {
      return { name: comm.title, value: comm.id };
    });
  }

  makeSearchDoc(enteredText) {
    const { workingGroupId, showStatus: status } = this.props;

    return {
      title: enteredText,
      workingGroupId,
      status,
      match: "W_CONTAINS",
    };
  }

  render() {
    return (
      <FilterableList
        onChange={(newValue) => this.props.valueWasSet(parseInt(newValue))}
        value={this.props.selectedCommissionId?.toString()}
        size={10}
        unfilteredContentFetchUrl={
          window.deploymentRootPath + "api/pluto/commission/list?length=150"
        }
        unfilteredContentConverter={CommissionSelector.convertContent}
        makeSearchDoc={this.makeSearchDoc}
        triggerRefresh={this.state.refreshCounter}
        allowCredentials={true}
      />
    );
  }
}

export default CommissionSelector;
