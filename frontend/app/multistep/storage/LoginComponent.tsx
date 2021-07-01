import React from "react";
import { TextField, Typography } from "@material-ui/core";

interface StorageLoginComponentProps {
  currentStorage: StorageType;
  valueWasSet: (newValue: StorageLoginDetails) => void;
  loginDetails: StorageLoginDetails;
}

const StorageLoginComponent: React.FC<StorageLoginComponentProps> = (props) => {
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
        <table className="full-width">
          <tbody>
            <tr>
              <td className="narrow">Storage host (or bucket name)</td>
              <td>
                <TextField
                  className="full-width"
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
              <td className="narrow">Storage port (if applicable)</td>
              <td>
                <TextField
                  className="full-width"
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
              <td className="narrow">
                Device (if applicable)
                <Typography className="explanation">
                  For ObjectMatrix storage, this is the "vault ID"
                </Typography>
              </td>
              <td>
                <TextField
                  className="full-width"
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
              <td className="narrow">User name</td>
              <td>
                <TextField
                  className="full-width"
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
              <td className="narrow">Password</td>
              <td>
                <TextField
                  className="full-width"
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
