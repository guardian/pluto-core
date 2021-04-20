import Multistep from "react-multistep";
import React from "react";
import axios from "axios";
import TemplateComponent from "./projectcreate/TemplateComponent.jsx";
import NameComponent from "./projectcreate/NameComponent.jsx";
import DestinationStorageComponent from "./projectcreate/DestinationStorageComponent.jsx";
import ProjectCompletionComponent from "./projectcreate/CompletionComponent.jsx";
import PlutoLinkageComponent from "./projectcreate/PlutoLinkageComponent.jsx";
import MediaRulesComponent from "./projectcreate/MediaRulesComponent.jsx";
import ProductionOfficeComponent from "./commissioncreate/ProductionOfficeComponent.jsx";

class ProjectCreateMultistep extends React.Component {
  constructor(props) {
    super(props);

    this.mounted = false;

    this.state = {
      projectTemplates: [],
      selectedProjectTemplate: null,
      storages: [],
      wgList: [],
      selectedWorkingGroup: null,
      selectedCommissionId: null,
      selectedStorage: null,
      projectName: "",
      projectFilename: "",
      lastError: null,
      deletable: false,
      deep_archive: true,
      sensitive: false,
      productionOffice: "UK",
    };

    this.templateSelectionUpdated = this.templateSelectionUpdated.bind(this);
    this.nameSelectionUpdated = this.nameSelectionUpdated.bind(this);
    this.storageSelectionUpdated = this.storageSelectionUpdated.bind(this);
    this.plutoDataUpdated = this.plutoDataUpdated.bind(this);
    this.rulesDataUpdated = this.rulesDataUpdated.bind(this);
  }

  requestDefaultProjectStorage(defaultValue) {
    return new Promise((resolve) =>
      axios
        .get("/api/default/project_storage_id")
        .then((response) => {
          const defaultStorage = parseInt(response.data.result.value);
          console.log("Got default storage of ", defaultStorage);
          resolve(defaultStorage);
        })
        .catch((error) => {
          if (error.response && error.response.status === 404) {
            console.log("No default storage has been set");
            resolve(defaultValue);
          } else {
            console.error(error);
            this.setState({ lastError: error });
          }
        })
    );
  }

  requestDefaultProjectTemplate(defaultValue) {
    return new Promise((resolve, reject) =>
      axios
        .get("/api/default/project_template_id")
        .then((response) => {
          const defaultTemplate = parseInt(response.data.result.value);
          console.log("Got default template with id. of ", defaultTemplate);
          resolve(defaultTemplate);
        })
        .catch((error) => {
          if (error.response && error.response.status === 404) {
            console.log("No default project template id. has been set");
            resolve(defaultValue);
          } else {
            console.error(error);
            this.setState({ lastError: error });
            reject(error);
          }
        })
    );
  }

  componentDidMount() {
    this.mounted = true;

    Promise.all([
      axios.get("/api/template"),
      axios.get("/api/storage"),
      axios.get("/api/pluto/workinggroup"),
    ])
      .then(([templates, storages, workingGroups]) => {
        const firstTemplate = templates.data.result[0]?.id ?? null;
        const firstStorage = storages.data.result[0]?.id ?? null;
        const firstWorkingGroup =
          workingGroups.data.result.filter((g) => !g.hide)[0]?.id ?? null;

        this.requestDefaultProjectStorage(firstStorage).then(
          (projectStorage) => {
            if (!this.mounted) {
              return;
            }
            this.requestDefaultProjectTemplate(firstTemplate).then(
              (projectTemplate) => {
                if (!this.mounted) {
                  return;
                }
                this.setState({
                  projectTemplates: templates.data.result,
                  selectedProjectTemplate: projectTemplate,
                  storages: storages.data.result,
                  selectedStorage: projectStorage,
                  wgList: workingGroups.data.result,
                  selectedWorkingGroup: firstWorkingGroup,
                });
              }
            );
          }
        );
      })
      .catch((error) => {
        console.error(error);
        this.setState({ lastError: error });
      });
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  componentDidUpdate(_prevProps, prevState) {
    if (prevState.selectedCommissionId === this.state.selectedCommissionId) {
      return;
    }

    console.log("commission id changed, updating production office too");

    if (this.state.selectedCommissionId == null) {
      this.setState({
        productionOffice: "UK",
      });

      return;
    }

    axios
      .get(`/api/pluto/commission/${this.state.selectedCommissionId}`)
      .then((result) => {
        this.setState({
          productionOffice: result.data.result.productionOffice,
        });
      })
      .catch((err) => {
        console.error(
          "Could not update production office from commission:",
          err
        );
      });
  }

  templateSelectionUpdated(newTemplate, cb) {
    this.setState({ selectedProjectTemplate: newTemplate }, cb);
  }

  nameSelectionUpdated(newNameState) {
    this.setState({
      projectName: newNameState.projectName,
      projectFilename: newNameState.fileName,
    });
  }

  storageSelectionUpdated(newStorage) {
    this.setState({ selectedStorage: newStorage });
  }

  plutoDataUpdated(newdata) {
    this.setState({
      selectedWorkingGroup: newdata.workingGroupRef,
      selectedCommissionId: newdata.plutoCommissionRef,
    });
  }

  rulesDataUpdated(newdata) {
    this.setState({
      deletable: newdata.deletable,
      deep_archive: newdata.deep_archive,
      sensitive: newdata.sensitive,
    });
  }

  render() {
    const steps = [
      {
        name: "Select project template",
        component: (
          <TemplateComponent
            templatesList={this.state.projectTemplates}
            selectedTemplate={this.state.selectedProjectTemplate?.toString()}
            selectionUpdated={this.templateSelectionUpdated}
          />
        ),
      },
      {
        name: "Name your project",
        component: (
          <NameComponent
            projectName={this.state.projectName}
            fileName={this.state.projectFilename}
            selectionUpdated={this.nameSelectionUpdated}
          />
        ),
      },
      {
        name: "Working Group & Commission",
        component: (
          <PlutoLinkageComponent
            valueWasSet={this.plutoDataUpdated}
            workingGroupList={this.state.wgList}
            currentWorkingGroup={this.state.selectedWorkingGroup}
          />
        ),
      },
      {
        name: "Production Office",
        component: (
          <ProductionOfficeComponent
            valueWasSet={(newValue) =>
              this.setState({ productionOffice: newValue })
            }
            value={this.state.productionOffice}
            extraText="  This should have been pre-set by your choice of commission but please check that it is correct before continuing"
          />
        ),
      },
      {
        name: "Media Rules",
        component: (
          <MediaRulesComponent
            valueWasSet={this.rulesDataUpdated}
            deletable={this.state.deletable}
            deep_archive={this.state.deep_archive}
            sensitive={this.state.sensitive}
          />
        ),
      },
    ];

    if (this.props.isAdmin) {
      steps.push({
        name: "Destination storage",
        component: (
          <DestinationStorageComponent
            storageList={this.state.storages}
            selectedStorage={this.state.selectedStorage}
            selectionUpdated={this.storageSelectionUpdated}
          />
        ),
      });
    }

    steps.push({
      name: "Summary",
      component: (
        <ProjectCompletionComponent
          projectTemplates={this.state.projectTemplates}
          selectedProjectTemplate={Number(this.state.selectedProjectTemplate)}
          storages={this.state.storages}
          selectedStorage={this.state.selectedStorage}
          projectName={this.state.projectName}
          projectFilename={this.state.projectFilename}
          selectedWorkingGroupId={this.state.selectedWorkingGroup}
          selectedCommissionId={this.state.selectedCommissionId}
          wgList={this.state.wgList}
          deletable={this.state.deletable}
          deep_archive={this.state.deep_archive}
          sensitive={this.state.sensitive}
          productionOffice={this.state.productionOffice}
        />
      ),
    });

    return <Multistep showNavigation={true} steps={steps} />;
  }
}

export default ProjectCreateMultistep;
