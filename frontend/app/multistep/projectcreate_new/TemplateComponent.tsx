import React, { useEffect, useState } from "react";
import axios from "axios";
import { SystemNotification, SystemNotifcationKind } from "pluto-headers";
import {
  LinearProgress,
  MenuItem,
  Select,
  Typography,
} from "@material-ui/core";
import { projectCreateStyles } from "./CommonStyles";

interface TemplateComponentProps {
  value?: number;
  valueDidChange: (newValue: number) => void;
}

const TemplateComponent: React.FC<TemplateComponentProps> = (props) => {
  const [knownProjectTemplates, setKnownProjectTemplates] = useState<
    ProjectTemplate[]
  >([]);
  const [defaultProjectTemplate, setDefaultProjectTemplate] = useState<
    number | undefined
  >(undefined);
  const [loading, setLoading] = useState(true);

  const classes = projectCreateStyles();

  //load in project type data at mount
  useEffect(() => {
    const loadDefaultProjectType = async () => {
      setLoading(true);
      const response = await axios.get<PlutoApiResponse<PlutoDefault>>(
        "/api/default/project_template_id",
        { validateStatus: () => true }
      );
      if (response.status == 200) {
        try {
          const numericId = parseInt(response.data.result.value);
          setDefaultProjectTemplate(numericId);
        } catch (err) {
          console.error("Could not get default project template id: ", err);
        }
      } else if (response.status != 404) {
        SystemNotification.open(
          SystemNotifcationKind.Error,
          "Could not load default project template"
        );
      }
    };

    const loadInData = async () => {
      setLoading(true);
      const response = await axios.get<PlutoApiResponse<ProjectTemplate[]>>(
        "/api/template",
        { validateStatus: () => true }
      );
      setLoading(false);
      switch (response.status) {
        case 200:
          if (response.data.result.length == 0) {
            console.error(
              "There are no project templates defined so we can't create a project"
            );
            SystemNotification.open(
              SystemNotifcationKind.Error,
              "There are no project templates defined in the system"
            );
          }
          setKnownProjectTemplates(
            response.data.result.filter((template) => !template.deprecated)
          );
          break;
        default:
          console.error(
            "Could not load in project types: server said ",
            response.status,
            " ",
            response.data
          );
          SystemNotification.open(
            SystemNotifcationKind.Error,
            "Could not load in project types"
          );
      }
    };

    loadDefaultProjectType()
      .then(() => loadInData())
      .catch((err) => {
        console.error(err);
        SystemNotification.open(
          SystemNotifcationKind.Error,
          "Could not set up template selector"
        );
      });
  }, []);

  //ensure that we have _a_ current value set if the known projects change
  useEffect(() => {
    if (!props.value && knownProjectTemplates.length > 0) {
      const defaultValue =
        defaultProjectTemplate ?? knownProjectTemplates[0].id;
      props.valueDidChange(defaultValue);
    }
  }, [knownProjectTemplates]);

  return (
    <div>
      <Typography variant={"h3"}>Select project template</Typography>
      <Typography>
        The first piece of information we need is a template to base your new
        project on.
      </Typography>
      <div className={classes.floatCentre}>
        {props.value ? (
          <Select
            value={props.value}
            onChange={(evt) => props.valueDidChange(evt.target.value as number)}
          >
            {knownProjectTemplates.map((template, idx) => (
              <MenuItem value={template.id} key={idx}>
                {template.name}
              </MenuItem>
            ))}
          </Select>
        ) : null}
        {loading ? <LinearProgress /> : null}
        {!loading && !props.value ? (
          <Typography>No project templates available</Typography>
        ) : null}
      </div>
    </div>
  );
};

export default TemplateComponent;
