import Multistep from "react-multistep";
import React from "react";
import axios from "axios";
import PlutoLinkageComponent from "./commissioncreate/PlutoLinkageComponent.jsx";
import CommissionTitleComponent from "./commissioncreate/CommissionTitleComponent.jsx";
import CommissionCompletionComponent from "./commissioncreate/CommissionCompletionComponent.jsx";
import ProductionOfficeComponent from "./commissioncreate/ProductionOfficeComponent.jsx";
import moment from "moment";
import { PropTypes } from "prop-types";

class CommissionCreateMultistep extends React.Component {
  static propTypes = {
    userName: PropTypes.string,
    match: PropTypes.object.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      wgList: [],
      selectedWorkingGroup: null,
      title: "",
      productionOffice: "UK", //FIXME: would be cool to guess this based on the users location data or timezone?
      scheduledCompletion: moment().add(1, "months").format("YYYY-MM-DD"),
      status: "New",
      owner: props.userName,
    };
  }

  componentDidMount() {
    let promises = [axios.get("/api/pluto/workinggroup")];
    if (
      this.props.match.params.hasOwnProperty("itemid") &&
      this.props.match.params.itemid !== "new"
    ) {
      promises.push(
        axios.get("/api/pluto/commission/" + this.props.match.params.itemid)
      );
    }
    Promise.all(promises)
      .then((responses) => {
        const firstWorkingGroup = responses[0].data.result.length
          ? responses[0].data.result[0].id
          : null;
        let stateToSet = {
          wgList: responses[0].data.result,
          selectedWorkingGroup: firstWorkingGroup,
        };

        if (responses.length > 1) {
          const loadedCommissionData = responses[1].data.result;
          stateToSet = Object.assign(stateToSet, {
            selectedWorkingGroup: loadedCommissionData.workingGroupId,
            title: loadedCommissionData.title,
            productionOffice: loadedCommissionData.productionOffice,
            status: loadedCommissionData.status,
            scheduledCompletion: moment(
              loadedCommissionData.scheduledCompletion
            ).format("YYYY-MM-DD"),
            owner: loadedCommissionData.owner,
          });
        }
        this.setState(stateToSet);
      })
      .catch((error) => {
        console.error(error);
        this.setState({ lastError: error });
      });
  }

  getWarnings() {
    let list = [];
    if (
      this.props.selectedWorkingGroupId === null ||
      this.props.selectedWorkingGroupId === 0
    )
      list.push("You need to select a working group");
    if (this.props.title === null || this.props.title === "")
      list.push("You need to choose a title");
    return list;
  }

  render() {
    const steps = [
      {
        name: "Working Group",
        component: (
          <PlutoLinkageComponent
            valueWasSet={(updatedContent) =>
              this.setState({
                selectedWorkingGroup: updatedContent.workingGroupRef,
              })
            }
            workingGroupList={this.state.wgList}
            currentWorkingGroup={this.state.selectedWorkingGroup}
          />
        ),
      },
      {
        name: "Title",
        component: (
          <CommissionTitleComponent
            selectionUpdated={(newTitle, newScheduledCompletion) =>
              this.setState({
                title: newTitle,
                scheduledCompletion: newScheduledCompletion,
              })
            }
            projectName={this.state.title}
            scheduledCompletion={this.state.scheduledCompletion}
          />
        ),
      },
      {
        name: "Production Office",
        component: (
          <ProductionOfficeComponent
            valueWasSet={(newValue) =>
              this.setState({ productionOffice: newValue })
            }
            value={this.state.productionOffice}
          />
        ),
      },
      {
        name: "Summary",
        component: (
          <CommissionCompletionComponent
            title={this.state.title}
            wgList={this.state.wgList}
            workingGroupId={this.state.selectedWorkingGroup}
            productionOffice={this.state.productionOffice}
            scheduledCompletion={this.state.scheduledCompletion}
            userName={this.state.owner}
          />
        ),
      },
    ];

    return <Multistep showNavigation={true} steps={steps} />;
  }
}

export default CommissionCreateMultistep;
