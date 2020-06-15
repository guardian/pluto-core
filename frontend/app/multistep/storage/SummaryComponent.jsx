import React from "react";
import ShowPasswordComponent from "../ShowPasswordComponent.jsx";
import PropTypes from "prop-types";

class SummaryComponent extends React.Component {
  static propTypes = {
    currentStorage: PropTypes.number.isRequired,
    strgTypes: PropTypes.array.isRequired,
    selectedType: PropTypes.number.isRequired,
    loginDetails: PropTypes.object.isRequired,
    rootpath: PropTypes.string.isRequired,
    clientpath: PropTypes.string.isRequired,
    enableVersions: PropTypes.bool,
    nickname: PropTypes.string,
    nicknameChanged: PropTypes.func.isRequired,
  };

  constructor(props) {
    super(props);
  }

  render() {
    return (
      <table>
        <tbody>
          <tr>
            <td>Storage type</td>
            <td id="storageType">{this.props.name}</td>
          </tr>
          <tr>
            <td>Login details</td>
            <td id="storageLoginDetails">
              <ul>
                {Object.entries(this.props.loginDetails).map((entry, index) =>
                  entry[0] && entry[1] ? (
                    <li key={index}>
                      <span className="login-description">{entry[0]}: </span>
                      <span className="login-value">
                        <ShowPasswordComponent
                          pass={entry[1]}
                          fieldName={entry[0]}
                        />
                      </span>
                    </li>
                  ) : (
                    <li key={index} />
                  )
                )}
              </ul>
            </td>
          </tr>
          <tr>
            <td>Versions enabled</td>
            <td
              className={this.props.enableVersions ? "" : "value-not-present"}
            >
              {this.props.enableVersions ? "Yes" : "No"}
            </td>
          </tr>
          <tr>
            <td>Subfolder</td>
            <td
              className={this.props.subfolder ? "" : "value-not-present"}
              id="storageSubfolder"
            >
              {this.props.subfolder ? this.props.subfolder : "(none)"}
            </td>
          </tr>
          <tr>
            <td>Client path</td>
            <td
              className={this.props.clientpath ? "" : "value-not-present"}
              id="storageClientPath"
            >
              {this.props.clientpath ? this.props.clientpath : "(none)"}
            </td>
          </tr>
          <tr>
            <td>Nickname</td>
            <td>
              <input
                type="text"
                value={this.props.nickname}
                onChange={this.props.nicknameChanged}
              />
            </td>
          </tr>
        </tbody>
      </table>
    );
  }
}

export default SummaryComponent;
