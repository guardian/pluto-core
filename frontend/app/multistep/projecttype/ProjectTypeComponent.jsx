import React from "react";
import CommonMultistepComponent from "../common/CommonMultistepComponent.jsx";
import MultistepComponentLoadsOnMount from "../common/LoadsOnMount.jsx";
import ErrorViewComponent from "../common/ErrorViewComponent.jsx";
import PlutoProjectTypeSelector from "../../Selectors/PlutoProjectTypeSelector.jsx";
import axios from "axios";
import {
  Table,
  TableBody,
  TableCell,
  TableRow,
  Input,
} from "@material-ui/core";

class ProjectTypeComponent extends MultistepComponentLoadsOnMount {
  /* CommonMultistepComponent includes an implementation of ComponentDidUpdate which
    updates the parent with our state
     */
  constructor(props) {
    super(props);

    this.endpoint = "/api/projecttype";

    this.state = {
      name: "",
      opensWith: "",
      version: "",
      fileExtension: "",
      loading: false,
      error: null,
    };
  }

  receivedExistingObject(projectType, cb) {
    /* called by the superclass when we get data back for an object */
    console.log("receivedExistingObject");
    this.setState(
      {
        name: projectType.result.name,
        version: projectType.result.targetVersion,
        opensWith: projectType.result.opensWith,
        fileExtension: projectType.result.fileExtension,
      },
      cb
    );
  }

  render() {
    if (this.state.error) {
      return <ErrorViewComponent error={this.state.error} />;
    }

    if (this.state.loading) return <p>Loading...</p>;

    return (
      <div style={{ height: 400 }}>
        <h3>Project Type</h3>
        <Table style={{ width: "inherit" }}>
          <TableBody>
            <TableRow>
              <TableCell>Name of project type</TableCell>
              <TableCell>
                <Input
                  id="project_type_name"
                  className="inputs"
                  value={this.state.name}
                  onChange={(event) =>
                    this.setState({ name: event.target.value })
                  }
                  style={{ width: 300 }}
                />
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>Opens with which Mac app?</TableCell>
              <TableCell>
                <Input
                  id="opens_with"
                  className="inputs"
                  value={this.state.opensWith}
                  onChange={(event) =>
                    this.setState({ opensWith: event.target.value })
                  }
                  style={{ width: 300 }}
                />
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>
                Minimum required software version to open it
              </TableCell>
              <TableCell>
                <Input
                  id="version"
                  className="inputs"
                  value={this.state.version}
                  onChange={(event) =>
                    this.setState({ version: event.target.value })
                  }
                  style={{ width: 300 }}
                />
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>File extension for this file type</TableCell>
              <TableCell>
                <Input
                  id="extension"
                  className="inputs"
                  value={this.state.fileExtension}
                  onChange={(event) =>
                    this.setState({ fileExtension: event.target.value })
                  }
                  style={{ width: 300 }}
                />
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    );
  }
}

export default ProjectTypeComponent;
