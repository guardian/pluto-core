import React from "react";
import PropTypes from "prop-types";
import CommonMultistepComponent from "../common/CommonMultistepComponent.jsx";
import WorkingGroupSelector from "../common/WorkingGroupSelectorNew";
import CommissionSelector from "../common/CommissionSelector.jsx";

class PlutoLinkageComponent extends CommonMultistepComponent {
  static propTypes = {
    valueWasSet: PropTypes.func.isRequired,
    currentWorkingGroup: PropTypes.number,
    workingGroupList: PropTypes.array.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      showStatus: "In Production",
    };
  }

  render() {
    return (
      <div>
        <h3>Select Working Group</h3>
        <p>
          We need to know which working group is undertaking this project. If
          you are unsure which to choose, please ask your commissioning editor.
        </p>
        <table>
          <tbody>
            <tr>
              <td>Working group</td>
              <td>
                <WorkingGroupSelector
                  valueWasSet={(value) =>
                    this.props.valueWasSet({ workingGroupRef: value })
                  }
                  workingGroupList={this.props.workingGroupList}
                  currentValue={this.props.currentWorkingGroup}
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  }
}

export default PlutoLinkageComponent;
