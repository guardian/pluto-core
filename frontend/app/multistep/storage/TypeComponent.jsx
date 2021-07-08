import React from "react";
import PropTypes from "prop-types";
import {
  FormControlLabel,
  MenuItem,
  Select,
  Switch,
  Typography,
} from "@material-ui/core";

class StorageTypeComponent extends React.Component {
  static propTypes = {
    strgTypes: PropTypes.array.isRequired,
    valueWasSet: PropTypes.func.isRequired,
    selectedType: PropTypes.number.isRequired,
    versionsAllowed: PropTypes.bool.isRequired,
    versionsAllowedChanged: PropTypes.func.isRequired,
  };

  constructor(props) {
    super(props);

    const initialStorage =
      this.props.strgTypes.length > 0 ? this.props.strgTypes[0] : null;

    this.state = {
      selectedType: 0,
      disableVersions: initialStorage ? initialStorage.canVersion : true,
    };

    this.selectorValueChanged = this.selectorValueChanged.bind(this);
  }

  selectorValueChanged(event) {
    const actualStorage = this.props.strgTypes[event.target.value];
    console.log(actualStorage);

    this.setState(
      {
        selectedType: event.target.value,
        disableVersions: actualStorage.hasOwnProperty("canVersion")
          ? !actualStorage.canVersion
          : true,
      },
      () => this.props.valueWasSet(parseInt(this.state.selectedType))
    );
  }

  render() {
    return (
      <>
        <Typography variant="h3">Storage Type</Typography>
        <Typography className="information">
          The first piece of information we need is what kind of storage to
          connect to.
          <br />
          Different storages require different configuration options; currently
          we support a local disk, ObjectMatrix vault, or an S3 bucket.
        </Typography>
        <table>
          <tbody>
            <tr>
              <td>
                <Select
                  id="storage_type_selector"
                  value={this.props.selectedType}
                  onChange={this.selectorValueChanged}
                >
                  {this.props.strgTypes.map((typeInfo, index) => (
                    <MenuItem key={index} value={index}>
                      {typeInfo.name}
                    </MenuItem>
                  ))}
                </Select>
              </td>
            </tr>
            <tr>
              <td>
                <FormControlLabel
                  control={
                    <Switch
                      checked={this.props.versionsAllowed}
                      onChange={(evt) =>
                        this.props.versionsAllowedChanged(evt.target.value)
                      }
                      disabled={this.state.disableVersions}
                    />
                  }
                  label="Enable versions"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </>
    );
  }
}

export default StorageTypeComponent;
