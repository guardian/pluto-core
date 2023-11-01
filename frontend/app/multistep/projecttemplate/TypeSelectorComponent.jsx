import React from "react";
import PropTypes from "prop-types";
import CommonMultistepComponent from "../common/CommonMultistepComponent.jsx";
import PlutoProjectTypeSelector from "../../Selectors/PlutoProjectTypeSelector.jsx";
import { Input, Select } from "@material-ui/core";

class TypeSelectorComponent extends CommonMultistepComponent {
  static propTypes = {
    projectTypes: PropTypes.array.isRequired,
    selectedPlutoSubtype: PropTypes.number.isRequired,
    selectedType: PropTypes.number.isRequired,
    templateName: PropTypes.string.isRequired,
    valueWasSet: PropTypes.func.isRequired,
    deprecated: PropTypes.bool.isRequired,
    loadingComplete: PropTypes.bool,
  };

  constructor(props) {
    super(props);

    this.state = {
      selectedType: props.selectedType,
      name: props.templateName,
      selectedPlutoSubtype: props.selectedPlutoSubtype,
      deprecated: props.deprecated,
    };

    this.selectorValueChanged = this.selectorValueChanged.bind(this);
  }

  componentDidUpdate(prevProps, prevState) {
    if (
      prevProps.loadingComplete === false &&
      this.props.loadingComplete === true
    ) {
      this.setState({
        selectedType: this.props.selectedType,
        name: this.props.templateName,
        selectedPlutoSubtype: this.props.selectedPlutoSubtype,
        deprecated: this.props.deprecated,
      });
    } else {
      super.componentDidUpdate(prevProps, prevState);
    }
  }

  selectorValueChanged(event) {
    this.setState(
      {
        name: this.props.templateName,
        selectedType: parseInt(event.target.value),
        deprecated: this.props.deprecated,
      },
      () => this.updateParent()
    );
  }

  projectTypeForId(projectTypeId) {
    for (let n = 0; n < this.props.projectTypes.length; ++n) {
      if (this.props.projectTypes[n].id === projectTypeId)
        return this.props.projectTypes[n];
    }
    return null;
  }

  // FIXME: this should return a number, but it can return a string, null, or something unknown (number?).
  getPlutoSubtypeForPlType() {
    const type = this.projectTypeForId(this.props.selectedType);
    if (!type) return "";

    return type?.plutoType ?? null;
  }

  render() {
    return (
      <div>
        <h3>Project Type and Name</h3>
        <p className="information">
          The first pieces of information we need are what kind of project this
          template represents and what it should be called. Please select from
          the list below. If the right type of project is not present, please{" "}
          <a href="/pluto-core/type/new">add</a> it and then come back to this
          form.
        </p>
        <ul style={{ listStyle: "none" }}>
          <li>
            <label htmlFor="project_type_selector">Project type:</label>
            <Select
              id="project_type_selector"
              value={this.props.selectedType}
              onChange={this.selectorValueChanged}
              style={{ marginLeft: 20 }}
            >
              {this.props.projectTypes.map((projectInfo, index) => (
                <option key={index} value={projectInfo.id}>
                  {projectInfo.name}
                </option>
              ))}
            </Select>
          </li>
          <li>
            <label htmlFor="projectNameSelector">Template name:</label>
            <Input
              type="text"
              id="projectNameSelector"
              value={this.state.name}
              onChange={(event) => this.setState({ name: event.target.value })}
              style={{ marginLeft: 20, width: 300 }}
            />
          </li>
          <li>
            <label htmlFor="projectDeprecatedSelector">Deprecated:</label>
            <input
              type="checkbox"
              id="projectDeprecatedSelector"
              checked={this.state.deprecated}
              onChange={(event) =>
                this.setState({ deprecated: event.target.checked })
              }
              style={{ marginLeft: 20 }}
            />
          </li>
        </ul>
      </div>
    );
  }

  updateParent() {
    this.props.valueWasSet(this.state);
  }
}

export default TypeSelectorComponent;
