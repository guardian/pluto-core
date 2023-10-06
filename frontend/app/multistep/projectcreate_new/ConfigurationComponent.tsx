import React, { useEffect, useState } from "react";
import { Grid, Paper, Switch, TextField, Typography } from "@material-ui/core";
import { format } from "date-fns";
import { useGuardianStyles } from "~/misc/utils";
import { isLoggedIn } from "~/utils/api";
import ObituaryComponent from "./ObituaryComponent";
import ProductionOfficeComponent from "./ProductionOfficeComponent";
import TemplateComponent from "./TemplateComponent";

interface ConfigurationComponentProps {
  templateValue?: number;
  templateValueDidChange: (newValue: number) => void;

  projectName: string;
  projectNameDidChange: (newValue: string) => void;
  fileName: string;
  fileNameDidChange: (newValue: string) => void;
  selectedStorageId: number | undefined;
  storageIdDidChange: (newValue: number) => void;

  valueDidChange: (newValue: string) => void;
  checkBoxDidChange: (newValue: boolean) => void;
  value: string;
  isObituary: boolean;

  valueWasSet: (newValue: ProductionOffice) => void;
  productionOfficeValue: string;
  extraText?: string;
}

const ConfigurationComponent: React.FC<ConfigurationComponentProps> = (
  props
) => {
  const [autoName, setAutoName] = useState(true);
  const [knownStorages, setKnownStorages] = useState<PlutoStorage[]>([]);
  const classes = useGuardianStyles();

  const [isAdmin, setIsAdmin] = useState<boolean>(false);

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

  const fetchWhoIsLoggedIn = async () => {
    try {
      console.log("Checking if user is admin");
      const loggedIn = await isLoggedIn();
      console.log("loggedin data: ", loggedIn);
      setIsAdmin(loggedIn.isAdmin);
    } catch {
      console.log("User is not logged in");
      setIsAdmin(false);
    }
  };

  useEffect(() => {
    fetchWhoIsLoggedIn();
  }, []);

  return (
    <div className={classes.common_box_size}>
      <Typography variant="h3">Project configuration</Typography>
      <Grid container direction="column">
        <Paper className={classes.paperWithPadding}>
          <Grid container spacing={4} alignItems="center">
            <Grid item xs={6} sm={6}>
              <TextField
                style={{ width: "100%" }}
                label="Project title"
                placeholder="Project title"
                helperText="Enter a good descriptive project name"
                margin="normal"
                id="projectNameInput"
                onChange={(event) =>
                  props.projectNameDidChange(event.target.value)
                }
                value={props.projectName}
              />
            </Grid>
            {isAdmin && (
              <>
                <Grid
                  container
                  item
                  xs={6}
                  sm={6}
                  alignItems="center"
                  justifyContent="flex-end"
                >
                  <Grid item xs={2}>
                    <Switch
                      id="autoNameCheck"
                      checked={autoName}
                      onChange={(event) => setAutoName(event.target.checked)}
                    />
                  </Grid>
                  <Grid item xs={8}>
                    <TextField
                      style={{ width: "100%" }}
                      id="fileNameInput"
                      onChange={(event) =>
                        props.fileNameDidChange(event.target.value)
                      }
                      value={props.fileName}
                      disabled={autoName}
                    />
                  </Grid>
                </Grid>
              </>
            )}
          </Grid>
        </Paper>

        <Paper className={classes.paperWithPadding}>
          <Grid item xs={12}>
            <TemplateComponent
              templateValue={props.templateValue}
              templateValueDidChange={props.templateValueDidChange}
            />
          </Grid>
        </Paper>
        <Paper className={classes.paperWithPadding}>
          <Grid item xs={12}>
            <ObituaryComponent
              valueDidChange={props.valueDidChange}
              value={props.value}
              isObituary={props.isObituary}
              checkBoxDidChange={props.checkBoxDidChange}
            />
          </Grid>
        </Paper>

        <Paper className={classes.paperWithPadding}>
          <Grid item xs={12}>
            <ProductionOfficeComponent
              productionOfficeValue={props.productionOfficeValue}
              valueWasSet={props.valueWasSet}
            />
          </Grid>
        </Paper>
      </Grid>
    </div>
  );
};

export default ConfigurationComponent;
