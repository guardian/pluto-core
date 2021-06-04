import React, { useContext, useEffect, useState } from "react";
import { Input, Switch, Typography } from "@material-ui/core";
import { makeStyles } from "@material-ui/core/styles";
import { format } from "date-fns";
import UserContext from "../../UserContext";

interface NameComponentProps {
  projectName: string;
  projectNameDidChange: (newValue: string) => void;
  fileName: string;
  fileNameDidChange: (newValue: string) => void;
}

const useStyles = makeStyles({
  inputBox: {
    width: "50vw",
    minWidth: "100px",
    maxWidth: "600px",
  },
});

const NameComponent: React.FC<NameComponentProps> = (props) => {
  const [autoName, setAutoName] = useState(true);

  const classes = useStyles();

  const makeAutoFilename = (title: string) => {
    const sanitizer = /[^\w\d_]+/g;
    return (
      format(new Date(), "yyyyMMdd") +
      "_" +
      title.substring(0, 32).replace(sanitizer, "_").toLowerCase()
    );
  };

  useEffect(() => {
    if (autoName) {
      props.fileNameDidChange(makeAutoFilename(props.projectName));
    }
  }, [props.projectName]);

  return (
    <div>
      <Typography variant="h3">Name your project</Typography>
      <Typography>
        Now, we need a descriptive name for your new project
      </Typography>
      <table>
        <tbody>
          <tr>
            <td>
              <Typography>Project Name</Typography>
            </td>
            <td>
              <Input
                className={classes.inputBox}
                id="projectNameInput"
                onChange={(event) =>
                  props.projectNameDidChange(event.target.value)
                }
                value={props.projectName}
              />
            </td>
          </tr>
          <tr>
            <td>
              <Typography>File name</Typography>
            </td>
            <td>
              <Input
                className={classes.inputBox}
                id="fileNameInput"
                onChange={(event) =>
                  props.fileNameDidChange(event.target.value)
                }
                value={props.fileName}
                disabled={autoName}
              />
            </td>
          </tr>
          <tr>
            <td>
              <Typography>Automatically name file (recommended)</Typography>
            </td>
            <td>
              <Switch
                id="autoNameCheck"
                checked={autoName}
                onChange={(event) => setAutoName(event.target.checked)}
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
};

export default NameComponent;
