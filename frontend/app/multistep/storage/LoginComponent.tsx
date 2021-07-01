import React from "react";
import { TextField, Typography } from "@material-ui/core";
import { multistepStyles } from "../common/CommonMultistepContainer";

interface StorageLoginComponentProps {
  currentStorage: StorageType;
  valueWasSet: (newValue: StorageLoginDetails) => void;
  loginDetails: StorageLoginDetails;
}

const StorageLoginComponent: React.FC<StorageLoginComponentProps> = (props) => {
  const classes = multistepStyles();

  if (!props.currentStorage.needsLogin) {
    return (
      <>
        <Typography variant="h3">Storage Login</Typography>
        <Typography>
          {props.currentStorage.name} storage does not require login.
        </Typography>
      </>
    );
  } else {
    return (
      <>
        <Typography variant="h3">Storage Login</Typography>
        <Typography>
          {props.currentStorage.name} is a remote storage. Please supply the
          information we need to log in to it. If any field is not applicable,
          leave it blank
        </Typography>
        <table className={classes.fullWidth}>
          <tbody>
            <tr>
              <td className={classes.labelCell}>
                Storage host (or bucket name)
              </td>
              <td>
                <TextField
                  className={classes.fullWidth}
                  id="hostname_input"
                  label="Host name"
                  value={props.loginDetails.hostname}
                  onChange={(event) =>
                    props.valueWasSet(
                      Object.assign({}, props.loginDetails, {
                        hostname: event.target.value,
                      })
                    )
                  }
                />
              </td>
            </tr>
            <tr>
              <td className={classes.labelCell}>
                Storage port (if applicable)
              </td>
              <td>
                <TextField
                  className={classes.fullWidth}
                  id="port_input"
                  label="Host port"
                  value={props.loginDetails.port.toString()}
                  onChange={(event) =>
                    props.valueWasSet(
                      Object.assign({}, props.loginDetails, {
                        port: parseInt(event.target.value),
                      })
                    )
                  }
                />
              </td>
            </tr>
            <tr>
              <td className={classes.labelCell}>
                Device (if applicable)
                <Typography className={classes.information}>
                  For ObjectMatrix storage, this is the "vault ID"
                </Typography>
              </td>
              <td>
                <TextField
                  className={classes.fullWidth}
                  id="device_input"
                  value={props.loginDetails.device}
                  label="Device"
                  onChange={(event) =>
                    props.valueWasSet(
                      Object.assign({}, props.loginDetails, {
                        device: event.target.value,
                      })
                    )
                  }
                />
              </td>
            </tr>
            <tr>
              <td className={classes.labelCell}>User name</td>
              <td>
                <TextField
                  className={classes.fullWidth}
                  id="username_input"
                  label="Username"
                  value={props.loginDetails.username}
                  onChange={(event) =>
                    props.valueWasSet(
                      Object.assign({}, props.loginDetails, {
                        username: event.target.value,
                      })
                    )
                  }
                />
              </td>
            </tr>
            <tr>
              <td className={classes.labelCell}>Password</td>
              <td>
                <TextField
                  className={classes.fullWidth}
                  id="password_input"
                  type="password"
                  label="Password"
                  value={props.loginDetails.password}
                  onChange={(event) =>
                    props.valueWasSet(
                      Object.assign({}, props.loginDetails, {
                        password: event.target.value,
                      })
                    )
                  }
                />
              </td>
            </tr>
          </tbody>
        </table>
      </>
    );
  }
};

export default StorageLoginComponent;
