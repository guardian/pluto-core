import React from "react";
import PropTypes from "prop-types";

class FilterTypeSelection extends React.Component {
  static propTypes = {
    type: PropTypes.string.isRequired,
    selectionChanged: PropTypes.func.isRequired,
    showFilters: PropTypes.bool.isRequired,
  };

  render() {
    const options = [
      { key: "W_CONTAINS", name: "string contains" },
      { key: "W_STARTSWITH", name: "string starts with" },
      { key: "W_ENDSWITH", name: "string ends with" },
      { key: "W_EXACT", name: "string matches exactly" },
    ];

    return (
      <ul
        style={{ display: this.props.showFilters ? "flex" : "none" }}
        className="horizontal-list-flex"
        onChange={(event) => this.props.selectionChanged(event.target.value)}
      >
        {options.map(({ key, name }) => (
          <li className="horizontal-list-item" key={key}>
            <input
              type="radio"
              value={key}
              name="filter-type-selection"
              defaultChecked={this.props.type === key}
            />
            {name}
          </li>
        ))}
      </ul>
    );
  }
}

export default FilterTypeSelection;
