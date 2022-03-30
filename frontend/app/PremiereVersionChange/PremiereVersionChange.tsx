import React, { useEffect, useState } from "react";
import axios from "axios";
import { Helmet } from "react-helmet";
import {
  Button,
  Grid,
  LinearProgress,
  Link,
  makeStyles,
  Paper,
  Typography,
} from "@material-ui/core";
import { RouteComponentProps } from "react-router-dom";
import { parse } from "query-string";
import {
  lookupProjectFile,
  lookupVersion,
  performConversion,
} from "./VersionChangeService";
import { Error } from "@material-ui/icons";
import { useHistory } from "react-router";
import {
  getOpenUrl,
  getStorageData,
  openProject,
} from "../ProjectEntryList/helpers";

const useStyles = makeStyles((theme) => ({
  centered: {
    marginTop: "0.4em",
    textAlign: "center",
  },
  error: {
    color: theme.palette.error.main,
  },
  buttonContainer: {
    marginTop: "1em",
    maxWidth: "800px",
    marginLeft: "auto",
    marginRight: "auto",
  },
}));

interface PremiereVersionChangeParams {
  project: string;
  requiredVersion: string;
}

const PremiereVersionChange: React.FC<RouteComponentProps> = (props) => {
  const classes = useStyles();
  const [loading, setLoading] = useState(true);

  const [lastError, setLastError] = useState<string | undefined>(undefined);
  const [projectName, setProjectName] = useState("");
  const [requiredVersion, setRequiredVersion] = useState("");
  const [targetVersionName, setTargetVersionName] = useState("");
  const [fileId, setFileId] = useState<number | undefined>(undefined);

  const [conversionInProgress, setConversionInProgress] = useState(false);
  const [newOpenUrl, setNewOpenUrl] = useState<string | undefined>(undefined);

  const history = useHistory();

  useEffect(() => {
    try {
      console.log("search string is ", props.location.search);

      const parsedParams = (parse(
        props.location.search
      ) as unknown) as PremiereVersionChangeParams;
      if (
        parsedParams.project == undefined ||
        parsedParams.requiredVersion == undefined
      ) {
        setLastError(
          "invalid arguments, missing either project or requiredVersion"
        );
        return;
      }
      console.log("parsedParams are ", parsedParams);
      setProjectName(parsedParams.project);
      setRequiredVersion(parsedParams.requiredVersion);
    } catch (err) {
      console.error("Could not parse url parameters: ", err);
      setLastError(err.toString());
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (requiredVersion != "") {
      lookupVersion(requiredVersion)
        .then((result) => setTargetVersionName(result.name))
        .catch((err) => setLastError(err))
        .finally(() => setLoading(false));
    }
  }, [requiredVersion]);

  useEffect(() => {
    if (projectName != "") {
      lookupProjectFile(projectName)
        .then((fileEntry) => setFileId(fileEntry.id))
        .catch((err) => setLastError(err))
        .finally(() => setLoading(false));
    }
  }, [projectName]);

  /**
   * User does not want to continue. If the window was opened directly then close it, otherwise go back to the root page.
   */
  const goBack = () => {
    if (document.referrer == "") {
      console.log("Page was opened directly");
      window.close();
    } else {
      history.push("/");
    }
  };

  /**
   * User wants to open the project anyway. Ask plutohelperagent to do so.
   */
  const forceOpen = () => {
    window.open(
      `pluto:openproject:${projectName}?premiereVersion=${requiredVersion}&force=true`,
      "_blank"
    );
  };

  /**
   * User wants to convert the project
   */
  const startConversion = async () => {
    setConversionInProgress(true); //this will disable the controlling buttons
    if (fileId && requiredVersion) {
      try {
        const updatedFile = await performConversion(fileId, requiredVersion);
        const openUrl = await getOpenUrl(updatedFile);
        setNewOpenUrl(openUrl);
      } catch (err) {
        console.error(err);
        setLastError(err.toString);
      }
    } else {
      console.error(
        "either fileId or requiredVersion was not set, can't start a conversion"
      );
    }
  };

  /**
   * try to open the new file once we have it
   */
  useEffect(() => {
    if (newOpenUrl && newOpenUrl != "") window.open(newOpenUrl, "_blank");
  }, [newOpenUrl]);

  return (
    <>
      <Helmet>
        <title>Change Premiere project version</title>
      </Helmet>
      <Paper elevation={3}>
        <Typography variant="h2">Change Premiere project's version</Typography>
        {loading || conversionInProgress ? <LinearProgress /> : undefined}
        {lastError ? (
          <Typography className={classes.centered}>
            <Error
              className={classes.error}
              style={{ verticalAlign: "bottom", marginRight: "0.2em" }}
            />
            {lastError}
          </Typography>
        ) : undefined}
        {!lastError && projectName && requiredVersion && targetVersionName ? (
          <Typography className={classes.centered}>
            In order to successfully open this project on your workstation, it
            must be updated to Premiere version {targetVersionName} (
            {requiredVersion}).
            <br />
            Click 'Update it' below to automatically complete this process. The
            existing project will be backed up in the system and the new version
            will be opened in Premiere.
            <br />
            If you have any issues with the converted project, please contact
            Multimediatech who can restore the old version for you.
          </Typography>
        ) : undefined}
        {!lastError && fileId && requiredVersion && !newOpenUrl ? (
          <Grid
            container
            spacing={3}
            justify="space-around"
            className={classes.buttonContainer}
          >
            <Grid item>
              <Button
                variant="contained"
                color="secondary"
                onClick={startConversion}
                disabled={conversionInProgress}
              >
                Update it
              </Button>
            </Grid>
            <Grid item>
              <Button
                variant="outlined"
                onClick={forceOpen}
                disabled={conversionInProgress}
              >
                Open anyway
              </Button>
            </Grid>
            <Grid item>
              <Button
                variant="outlined"
                onClick={goBack}
                disabled={conversionInProgress}
              >
                Don't try to open it
              </Button>
            </Grid>
          </Grid>
        ) : undefined}
        {newOpenUrl ? (
          <Typography className={classes.centered}>
            The new project should open automatically in a few moments. If it
            does not, try clicking <Link href={newOpenUrl}>here</Link> to open
            it manually
          </Typography>
        ) : undefined}
      </Paper>
    </>
  );
};

export default PremiereVersionChange;
