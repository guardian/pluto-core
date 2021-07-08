import React from "react";
import ShowPasswordComponent from "../ShowPasswordComponent.jsx";
import { TextField, Typography } from "@material-ui/core";
import { multistepStyles } from "../common/CommonMultistepContainer";
import StorageEntryView from "../../EntryViews/StorageEntryView";

interface SummaryComponentProps {
  storageType: StorageType;
  loginDetails: StorageLoginDetails;
  rootPath: string;
  clientPath: string;
  enableVersions: boolean;
  backsUpTo: number | undefined;
  nickName: string;
  nickNameChanged?: (newValue: string) => void;
}

const SummaryComponent: React.FC<SummaryComponentProps> = (props) => {
  const classes = multistepStyles();

  return (
    <table>
      <tbody>
        <tr>
          <td className={classes.labelCell}>Storage type</td>
          <td id="storageType" className={classes.fullWidth}>
            {props.storageType.name}
          </td>
        </tr>
        <tr>
          <td className={classes.labelCell} style={{ verticalAlign: "middle" }}>
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
          <td className={classes.labelCell}>Backing up to</td>
          <td className={classes.fullWidth} id="backsUpTo">
            {props.backsUpTo ? (
              <StorageEntryView entryId={props.backsUpTo} />
            ) : (
              <Typography className={classes.information}>
                No backup configured
              </Typography>
            )}
          </td>
        </tr>
        <tr>
          <td className={classes.labelCell}>Nickname</td>
          <td>
            {props.nickNameChanged ? (
              <TextField
                type="text"
                value={props.nickName}
                onChange={(evt) => {
                  if (props.nickNameChanged)
                    props.nickNameChanged(evt.target.value);
                }}
              />
            ) : (
              props.nickName
            )}
          </td>
        </tr>
      </tbody>
    </table>
  );
};

export default SummaryComponent;
