import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet";
import {
  Button,
  Grid,
  LinearProgress,
  Link,
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
import { CheckCircle, Error } from "@material-ui/icons";
import { useHistory } from "react-router";
import {
  getFileStorageMetadata,
  getOpenUrl,
} from "../ProjectEntryList/helpers";
import { useGuardianStyles } from "~/misc/utils";

const largestSupportedFile = 10485760; //don't try to convert anything bigger than 10meg, it's unreliable

interface PremiereVersionChangeParams {
  project: string;
  requiredVersion: string;
}

const PremiereVersionChange: React.FC<RouteComponentProps> = (props) => {
  const classes = useGuardianStyles();
  const [loading, setLoading] = useState(true);

  const [lastError, setLastError] = useState<string | undefined>(undefined);
  const [projectName, setProjectName] = useState("");
  const [requiredVersion, setRequiredVersion] = useState("");
  const [targetVersionName, setTargetVersionName] = useState("");
  const [fileId, setFileId] = useState<number | undefined>(undefined);
  const [fileSize, setFileSize] = useState<number | undefined>(undefined);
  const [failedLarge, setFailedLarge] = useState(false);
  const [conversionInProgress, setConversionInProgress] = useState(false);
  const [newOpenUrl, setNewOpenUrl] = useState<string | undefined>(undefined);
  const [popupBlocked, setPopupBlocked] = useState(false);

  const history = useHistory();

  useEffect(() => {
    try {
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

  useEffect(() => {
    if (!!fileId) {
      setLoading(true);
      getFileStorageMetadata(fileId)
        .then((meta) => {
          console.log("Got file metadata of ", meta, " for ", fileId);
          const sizeString = meta.get("size");
          const sizeNum = sizeString ? parseInt(sizeString, 10) : undefined;
          console.log("File size is ", sizeNum);
          setFileSize(sizeNum);
        })
        .catch((err) => {
          console.error(
            "Could not get file storage metadata for ",
            fileId,
            ": ",
            err
          );
        })
        .finally(() => setLoading(false));
    }
  }, [fileId]);

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

  const forceOpenUrl = () =>
    `pluto:openproject:${projectName}?premiereVersion=${requiredVersion}&force=true`;

  /**
   * User wants to open the project anyway. Ask plutohelperagent to do so.
   */
  const forceOpen = () => {
    window.open(forceOpenUrl(), "_blank");
  };

  /**
   * User wants to convert the project
   */
  const startConversion = async () => {
    setConversionInProgress(true); //this will disable the controlling buttons
    if (fileId && requiredVersion) {
      try {
        const updatedFile = await performConversion(fileId, requiredVersion);
        const openUrl = await getOpenUrl(updatedFile, -1);
        setNewOpenUrl(openUrl);
        setConversionInProgress(false);
      } catch (err) {
        if (err == "The target file is already at the requested version") {
          setLastError("No update was required");
          setNewOpenUrl(forceOpenUrl);
          setConversionInProgress(false);
        } else {
          console.error(err);
          if (fileSize && fileSize > largestSupportedFile) {
            setFailedLarge(true);
          }
          if (typeof err == "string") {
            setLastError(err);
          } else {
            setLastError(err.toString());
          }
          setConversionInProgress(false);
        }
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
    if (newOpenUrl && newOpenUrl !== "") {
      const newWindow = window.open(newOpenUrl, "_blank");
      if (!newWindow) {
        setPopupBlocked(true);
      } else {
        // Successfully opened a new window, close this one.
        window.close();
      }
    }
  }, [newOpenUrl]);

  return (
    <>
      <Helmet>
        <title>Change Premiere project version</title>
      </Helmet>
      <Paper elevation={3}>
        <Typography variant="h2" style={{ textAlign: "center" }}>
          Change Premiere project's version
        </Typography>
        {loading || conversionInProgress ? <LinearProgress /> : undefined}
        {failedLarge ? (
          <Typography className={classes.centered}>
            This project is too large to be automatically updated to a newer
            version of Adobe Premiere by Pluto. Please contact
            multimediatech@theguardian.com for assistance.
            <br />
            <br />
            If you need to open this urgently then make sure you choose the
            "Open Anyway" button. As the project opens, Premiere Pro will
            request you to rename the project file. Make sure you remove the
            "_1" bit that is added to the end of the filename and click save.
            Then let it overwrite when prompted.
          </Typography>
        ) : undefined}
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
            must be updated to the Premiere version {targetVersionName} (
            {requiredVersion}) format.
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
            justifyContent="space-around"
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
        {popupBlocked ? (
          <Grid container direction="column" spacing={2}>
            <Grid
              item
              style={{
                marginLeft: "auto",
                marginRight: "auto",
                width: "100px",
              }}
            >
              <CheckCircle className={classes.success} />
            </Grid>
            <Grid item>
              <Typography
                className={classes.centered}
                style={{ fontWeight: "bold", fontSize: "1.5em" }}
              >
                Ready to go! Please click{" "}
                <Button
                  variant="contained"
                  color="primary"
                  onClick={() => {
                    const newWindow = window.open(newOpenUrl);
                    if (newWindow) {
                      window.close();
                    }
                  }}
                  style={{ cursor: "pointer" }}
                >
                  here
                </Button>{" "}
                to open project
              </Typography>
            </Grid>
          </Grid>
        ) : undefined}
        {newOpenUrl || lastError ? (
          <Typography className={classes.centered}>
            You can now close this tab
          </Typography>
        ) : undefined}
      </Paper>
    </>
  );
};

export default PremiereVersionChange;
