import React from "react";
import ShowPasswordComponent from "../ShowPasswordComponent.jsx";
import { TextField, Typography } from "@material-ui/core";
import { multistepStyles } from "../common/CommonMultistepContainer";

interface SummaryComponentProps {
  //strgTypes: StorageType[];
  //selectedType: number;
  storageType: StorageType;
  loginDetails: StorageLoginDetails;
  rootPath: string;
  clientPath: string;
  enableVersions: boolean;
  nickName: string;
  nickNameChanged: (newValue: string) => void;
}

const SummaryComponent: React.FC<SummaryComponentProps> = (props) => {
  const classes = multistepStyles();

  // const selectedTypeName = () => {
  //   return props.strgTypes.length > props.selectedType
  //     ? props.strgTypes[props.selectedType].name
  //     : "";
  // };
  //
  // const selectedTypeCanLogin = () => {
  //   return props.strgTypes.length > props.selectedType ? props.strgTypes[props.selectedType].
  // }
  // const storageTypeValid = () =>
  //   props.strgTypes.length > props.selectedType && props.strgTypes.length > 0;

  return (
    <>
      <Typography variant="h3">Set up storage</Typography>
      <Typography>
        We will set up a new storage definition with the information below.
      </Typography>
      <Typography>
        Press "Confirm" to go ahead, or press Previous if you need to amend any
        details.
      </Typography>
      <table>
        <tbody>
          <tr>
            <td className={classes.labelCell}>Storage type</td>
            <td id="storageType" className={classes.fullWidth}>
              {props.storageType.name}
            </td>
          </tr>
          <tr>
            <td
              className={classes.labelCell}
              style={{ verticalAlign: "middle" }}
            >
              Login details
            </td>
            <td id="storageLoginDetails" className={classes.fullWidth}>
              {props.storageType.needsLogin ? (
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
              ) : (
                <Typography className={classes.information}>
                  Login not required
                </Typography>
              )}
            </td>
          </tr>
          <tr>
            <td className={classes.labelCell}>Versions enabled</td>
            <td className={classes.fullWidth}>
              {props.enableVersions ? "Yes" : "No"}
            </td>
          </tr>
          <tr>
            <td className={classes.labelCell}>Subfolder</td>
            <td className={classes.fullWidth} id="storageSubfolder">
              {props.rootPath ? props.rootPath : "(none)"}
            </td>
          </tr>
          <tr>
            <td className={classes.labelCell}>Client path</td>
            <td className={classes.fullWidth} id="storageClientPath">
              {props.clientPath ? props.clientPath : "(none)"}
            </td>
          </tr>
          <tr>
            <td className={classes.labelCell}>Nickname</td>
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
    </>
  );
};

export default SummaryComponent;
