import React from "react";
import axios from "axios";
import StorageSelector from "./Selectors/StorageSelector.jsx";
import ErrorViewComponent from "./multistep/common/ErrorViewComponent.jsx";
import TemplateSelector from "./Selectors/TemplateSelector.jsx";
import { Helmet } from "react-helmet";

class ServerDefaults extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      currentValues: {},
      storageList: [],
      templatesList: [],
      loading: false,
      error: null,
    };

    this.keys = {
      storage: "project_storage_id",
      projectTemplate: "project_template_id",
    };

    this.updateDefaultSetting = this.updateDefaultSetting.bind(this);
    this.updateDefaultProjectTemplateSetting = this.updateDefaultProjectTemplateSetting.bind(
      this
    );
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
      ])
        .then(([defaults, storage, template]) => {
          this.setState({
            loading: false,
            error: null,
            templatesList: template.data.result,
            storageList: storage.data.result,
            currentValues: defaults.data.results.reduce((acc, entry) => {
              acc[entry.name] = entry.value;
              return acc;
            }, {}),
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

  updateDefaultProjectTemplateSetting(newTemplateId, keyname) {
    axios
      .put("/api/default/" + keyname, newTemplateId, {
        headers: { "Content-Type": "text/plain" },
      })
      .then(window.setTimeout(() => this.refreshData(), 250));
    return;
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
  templatePref() {
    if (this.state.currentValues.hasOwnProperty(this.keys.projectTemplate)) {
      return this.state.currentValues[this.keys.projectTemplate];
    } else {
      if (this.state.templatesList.length > 0)
        return this.state.templatesList[0].id;
      else return 0;
    }
  }

  render() {
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
              <td>Default storage for created projects</td>
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
              <td>Default project template</td>
              <td>
                <TemplateSelector
                  selectedTemplate={this.templatePref()}
                  selectionUpdated={(value) =>
                    this.updateDefaultProjectTemplateSetting(
                      value,
                      this.keys.projectTemplate
                    )
                  }
                  templatesList={this.state.templatesList}
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
