import React from "react";
import PropTypes from "prop-types";
import StatusIndicator from "../EntryViews/StatusIndicator.jsx";

class StorageSelector extends React.Component {
  static propTypes = {
    selectedStorage: PropTypes.number,
    selectionUpdated: PropTypes.func.isRequired,
    storageList: PropTypes.array.isRequired,
    enabled: PropTypes.bool.isRequired,
    showLabel: PropTypes.bool,
  };

  getSelectedStorageRecord() {
    if (!this.props.selectedStorage) {
      return null;
    }

    const results = this.props.storageList.filter(
      (entry) => entry.id === this.props.selectedStorage
    );

    return results[0] ?? null;
  }

  getSelectedStatus() {
    const { status = "hidden" } = this.getSelectedStorageRecord() ?? {};

    return status;
  }

  displayName(storage) {
    if (storage.nickname && storage.nickname !== "") {
      return `${storage.nickname} [${storage.storageType}]`;
    } else {
      return `${storage.rootpath} on ${storage.storageType}`;
    }
  }

  render() {
    return (
      <span>
        <select
          id="storageSelector"
          value={this.props.selectedStorage}
          disabled={!this.props.enabled}
          style={{ marginRight: "1em" }}
          onChange={(event) =>
            this.props.selectionUpdated(parseInt(event.target.value))
          }
        >
          {this.props.storageList.map((storage) => (
            <option key={storage.id} value={storage.id}>
              {this.displayName(storage)}
            </option>
          ))}
        </select>
        <StatusIndicator
          status={this.getSelectedStatus()}
          showLabel={this.props.showLabel}
        />
      </span>
    );
  }
}

export default StorageSelector;
