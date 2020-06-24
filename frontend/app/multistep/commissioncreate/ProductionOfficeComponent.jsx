import React from "react";
import PropTypes from "prop-types";

class ProductionOfficeComponent extends React.Component {
  static propTypes = {
    valueWasSet: PropTypes.func.isRequired,
    value: PropTypes.string.isRequired,
    extraText: PropTypes.string,
  };

  //we could load these in dynamically from the backend, but as they hardly ever change it seems better
  //to go with the more efficient approach of defining them here
  static validProductionOffices = ["UK", "US", "Aus"];

  render() {
    return (
      <div>
        <h3>Where are you working from</h3>
        <p>
          We need to know which production office you are working out of, i.e.
          where the commissioner who green-lit this project usually works.
          {this.props.extraText ? this.props.extraText : ""}
        </p>
        <table>
          <tbody>
            <tr>
              <td>Production Office</td>
              <td>
                <select
                  id="production-office-selector"
                  value={this.props.value}
                  onChange={(evt) => this.props.valueWasSet(evt.target.value)}
                >
                  {ProductionOfficeComponent.validProductionOffices.map(
                    (officeName, idx) => (
                      <option key={idx} value={officeName}>
                        {officeName}
                      </option>
                    )
                  )}
                </select>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  }
}

export default ProductionOfficeComponent;
