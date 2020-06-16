import React from "react";
import PropTypes from "prop-types";
import { TextField } from "@material-ui/core";
import CommonMultistepComponent from "../common/CommonMultistepComponent.jsx";
import moment from "moment";

class CommissionTitleComponent extends CommonMultistepComponent {
  static propTypes = {
    projectName: PropTypes.string.isRequired,
    selectionUpdated: PropTypes.func.isRequired,
    scheduledCompletion: PropTypes.string.isRequired,
  };

  render() {
    return (
      <div>
        <h3>Name your commission</h3>
        <p>
          Now, we need a descriptive name for your new commission and we need to
          know when it is scheduled to be completed.
        </p>
        <p>
          This value can be updated at any time in the future, so don't worry
          too much about setting something very long now. Beware, if you set a
          value too far in the future, it is possible that at some point in the
          future MM Tech will conclude that your commission was actually
          finished long ago and archive it; so please try to be accurate.
        </p>
        <table>
          <tbody>
            <tr>
              <td>Commission Name</td>
              <td>
                <input
                  id="project-name-input"
                  onChange={(evt) =>
                    this.props.selectionUpdated(
                      evt.target.value,
                      this.props.scheduledCompletion
                    )
                  }
                  value={this.props.projectName}
                />
              </td>
            </tr>
            <tr>
              <td>Scheduled Completion</td>
              <td>
                <TextField
                  id="scheduled-completion-input"
                  label="Scheduled Completion"
                  type="date"
                  value={this.props.scheduledCompletion}
                  onChange={(evt) =>
                    this.props.selectionUpdated(
                      this.props.projectName,
                      evt.target.value
                    )
                  }
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  }
}

export default CommissionTitleComponent;
