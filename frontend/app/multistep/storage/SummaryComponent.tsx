import React from "react";
import ShowPasswordComponent from "../ShowPasswordComponent.jsx";
import PropTypes, { number } from "prop-types";
import { TextField } from "@material-ui/core";

interface SummaryComponentProps {
  currentStorage: number;
  strgTypes: StorageType[];
  selectedType: number;
  loginDetails: StorageLoginDetails;
  rootPath: string;
  clientPath: string;
  enableVersions: boolean;
  nickName: string;
  nickNameChanged: (newValue: string) => void;
}

const SummaryComponent: React.FC<SummaryComponentProps> = (props) => {
  const selectedTypeName = () => {
    return props.strgTypes.length > props.selectedType
      ? props.strgTypes[props.selectedType].name
      : "";
  };

  const storageTypeValid = () =>
    props.strgTypes.length > props.selectedType && props.strgTypes.length > 0;

  return (
    <table>
      <tbody>
        <tr>
          <td>Storage type</td>
          <td id="storageType">{selectedTypeName()}</td>
        </tr>
        <tr>
          <td>Login details</td>
          <td id="storageLoginDetails">
            <ul>
              {Object.entries(props.loginDetails).map((entry, index) =>
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
          <td className={props.enableVersions ? "" : "value-not-present"}>
            {props.enableVersions ? "Yes" : "No"}
          </td>
        </tr>
        <tr>
          <td>Subfolder</td>
          <td
            className={props.rootPath ? "" : "value-not-present"}
            id="storageSubfolder"
          >
            {props.rootPath ? props.rootPath : "(none)"}
          </td>
        </tr>
        <tr>
          <td>Client path</td>
          <td
            className={props.clientPath ? "" : "value-not-present"}
            id="storageClientPath"
          >
            {props.clientPath ? props.clientPath : "(none)"}
          </td>
        </tr>
        <tr>
          <td>Nickname</td>
          <td>
            <TextField
              type="text"
              value={props.nickName}
              onChange={(evt) => props.nickNameChanged(evt.target.value)}
            />
          </td>
        </tr>
      </tbody>
    </table>
  );
};

export default SummaryComponent;
