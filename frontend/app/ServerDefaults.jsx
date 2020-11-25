import React from "react";
import axios from "axios";
import StorageSelector from "./Selectors/StorageSelector.jsx";
import ErrorViewComponent from "./multistep/common/ErrorViewComponent.jsx";
import { Helmet } from "react-helmet";
import PostrunSelector from "./Selectors/PostrunSelector";

class ServerDefaults extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      currentValues: {},
      storageList: [],
      templatesList: [],
      plutoProjectTypes: [],
      loading: false,
      error: null,
    };

    this.keys = {
      storage: "project_storage_id",
      assetFolder: "asset_folder_action",
    };

    this.updateDefaultSetting = this.updateDefaultSetting.bind(this);
  }

  componentDidMount() {
    this.refreshData();
  }

  refreshData() {
    this.setState({ loading: true }, () => {
      Promise.all([
        axios.get("/api/default"),
        axios.get("/api/storage"),
        axios.get("/api/template"),
        axios.get("/api/plutoprojecttypeid"),
      ])
        .then(([defaults, storage, template, projectTypeId]) => {
          this.setState({
            loading: false,
            error: null,
            templatesList: template.data.result,
            storageList: storage.data.result,
            currentValues: defaults.data.results.reduce((acc, entry) => {
              acc[entry.name] = entry.value;
              return acc;
            }, {}),
            plutoProjectTypes: projectTypeId.data.result,
          });
        })
        .catch((error) => this.setState({ loading: false, error: error }));
    });
  }

  updateDefaultSetting(newStorageId, keyname) {
    axios
      .put("/api/default/" + keyname, newStorageId, {
        headers: { "Content-Type": "text/plain" },
      })
      .then(window.setTimeout(() => this.refreshData(), 250));
  }

  /* return the current default storage, or first in the list, or zero if neither is present */
  storagePref() {
    if (this.state.currentValues.hasOwnProperty(this.keys.storage)) {
      return this.state.currentValues[this.keys.storage];
    } else {
      if (this.state.storageList.length > 0)
        return this.state.storageList[0].id;
      else return 0;
    }
  }

  /* return the current default template, or first in the list, or zero if neither is present */
  templatePref(keyName) {
    const { currentValues, templatesList } = this.state;

    return currentValues[keyName] ?? templatesList[0]?.id ?? 0;
  }

  render() {
    const plutoProjectTypeList = Object.assign(
      [],
      this.state.plutoProjectTypes
    ).sort((a, b) => {
      if (a.name < b.name) return -1;
      if (a.name > b.name) return 1;
      return 0;
    });

    return (
      <div className="mainbody">
        <Helmet>
          <title>Core Admin</title>
        </Helmet>
        <h3>Server defaults</h3>
        {this.state.error && <ErrorViewComponent error={this.state.error} />}
        <table>
          <tbody>
            <tr>
              <td style={{ verticalAlign: "middle" }}>
                Default storage for created projects
              </td>
              <td>
                <StorageSelector
                  enabled={true}
                  selectedStorage={this.storagePref()}
                  selectionUpdated={(value) =>
                    this.updateDefaultSetting(value, this.keys.storage)
                  }
                  storageList={this.state.storageList}
                />
              </td>
            </tr>
            <tr>
              <td style={{ verticalAlign: "middle" }}>
                Postrun action to run for "re-create asset folder" action
              </td>
              <td>
                <PostrunSelector
                  onChange={(newValue) =>
                    this.updateDefaultSetting(newValue, this.keys.assetFolder)
                  }
                  value={
                    this.state.currentValues.hasOwnProperty(
                      this.keys.assetFolder
                    )
                      ? this.state.currentValues[this.keys.assetFolder]
                      : undefined
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

export default ServerDefaults;
