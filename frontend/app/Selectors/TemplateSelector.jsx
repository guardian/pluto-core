import React from "react";
import PropTypes from "prop-types";
import { Select } from "@material-ui/core";

class TemplateSelector extends React.Component {
  static propTypes = {
    selectedTemplate: PropTypes.string,
    selectionUpdated: PropTypes.func.isRequired,
    templatesList: PropTypes.array.isRequired,
    allowNull: PropTypes.bool,
  };

  getTemplatesList() {
    if (this.props.allowNull) {
      return [{ id: -1, name: "(not set)", deprecated: false }].concat(
        this.props.templatesList
      );
    } else {
      return this.props.templatesList;
    }
  }

  render() {
    return (
      <Select
        id="project_template_selector"
        value={this.props.selectedTemplate ?? undefined}
        onChange={(event) => this.props.selectionUpdated(event.target.value)}
      >
        {this.getTemplatesList()
          .filter(({ deprecated }) => !deprecated)
          .map(({ id, name }) => (
            <option key={id} value={id}>
              {name}
            </option>
          ))}
      </Select>
    );
  }
}

export default TemplateSelector;
