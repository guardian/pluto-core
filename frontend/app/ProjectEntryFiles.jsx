import React from "react";
import axios from "axios";
import PropTypes from "prop-types";
import FileOnStorageView from "./EntryViews/FileOnStorageView.jsx";

class ProjectEntryFiles extends React.Component {
  static propTypes = {
    entryId: PropTypes.number.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      loading: false,
      lastError: null,
      filesList: [],
    };
  }

  componentDidMount() {
    this.setState({ loading: true }, () =>
      axios
        .get("/api/project/" + this.props.entryId + "/files")
        .then((response) =>
          this.setState({ loading: false, filesList: response.data.files })
        )
        .catch((error) => this.setState({ lastError: error, loading: false }))
    );
  }

  render() {
    return (
      <ul>
        {this.state.filesList.map((entry) => (
          <li>
            <FileOnStorageView
              entryId={entry.storage}
              filepath={entry.filepath}
            />
          </li>
        ))}
      </ul>
    );
  }
}

export default ProjectEntryFiles;
