import React from "react";
import PropTypes from "prop-types";

class PostrunActionSelector extends React.Component {
  static propTypes = {
    actionsList: PropTypes.array.isRequired,
    valueWasSet: PropTypes.func.isRequired,
    selectedEntries: PropTypes.array,
    shouldExclude: PropTypes.array,
  };

  checkboxUpdated(event, selectedId, cb) {
    if (!event.target.checked) {
      const updatedEntries = this.props.selectedEntries.filter(
        (value) => value !== selectedId
      );
      cb(updatedEntries);
    } else {
      const newval = this.props.selectedEntries.concat(selectedId);
      cb(newval);
    }
  }

  /* if shouldExclude is present, filter those out */
  filteredActions() {
    if (this.props.shouldExclude) {
      return this.props.actionsList.filter(
        (entry) => !this.props.shouldExclude.includes(entry.id)
      );
    } else {
      return this.props.actionsList;
    }
  }

  render() {
    return (
      <ul className="selection-list">
        {this.filteredActions().map((action) => (
          <li className="selection-list" key={"action-" + action.id}>
            <label className="selection-list">
              <input
                className="selection-list"
                id={"action-check-" + action.id}
                type="checkbox"
                onChange={(event) =>
                  this.checkboxUpdated(event, action.id, this.props.valueWasSet)
                }
                checked={this.props.selectedEntries.includes(action.id)}
              />
              {action.title}
            </label>
          </li>
        ))}
      </ul>
    );
  }
}

export default PostrunActionSelector;
