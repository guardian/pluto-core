import React, { useEffect, useState } from "react";
import axios from "axios";
import SystemNotification, {
  SystemNotificationKind,
} from "../../SystemNotification";
import {
  LinearProgress,
  MenuItem,
  Select,
  Typography,
} from "@material-ui/core";
import { makeStyles } from "@material-ui/core/styles";

interface TemplateComponentProps {
  value?: number;
  valueDidChange: (newValue: number) => void;
}

const useStyles = makeStyles({
  floatCentre: {
    width: "fit-content",
    marginLeft: "auto",
    marginRight: "auto",
  },
});
const TemplateComponent: React.FC<TemplateComponentProps> = (props) => {
  const [knownProjectTypes, setKnownProjectTypes] = useState<ProjectType[]>([]);
  const [loading, setLoading] = useState(true);

  const classes = useStyles();

  //load in project type data at mount
  useEffect(() => {
    const loadInData = async () => {
      setLoading(true);
      const response = await axios.get<PlutoApiResponse<ProjectType[]>>(
        "/api/projecttype",
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
              SystemNotificationKind.Error,
              "There are no project templates defined in the system"
            );
          }
          setKnownProjectTypes(response.data.result);
          break;
        default:
          console.error(
            "Could not load in project types: server said ",
            response.status,
            " ",
            response.data
          );
          SystemNotification.open(
            SystemNotificationKind.Error,
            "Could not load in project types"
          );
      }
    };

    loadInData();
  }, []);

  //ensure that we have _a_ current value set if the known projects change
  useEffect(() => {
    if (!props.value && knownProjectTypes.length > 0)
      props.valueDidChange(knownProjectTypes[0].id);
  }, [knownProjectTypes]);

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
            {knownProjectTypes.map((type, idx) => (
              <MenuItem value={type.id} key={idx}>
                {type.name} (version {type.targetVersion})
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
