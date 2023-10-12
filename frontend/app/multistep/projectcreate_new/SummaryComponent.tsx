import React, { useContext } from "react";
import { Divider, Grid, Paper, Typography } from "@material-ui/core";
import ProjectTemplateEntryView from "../../EntryViews/ProjectTemplateEntryView";
import StorageEntryView from "../../EntryViews/StorageEntryView";
import WorkingGroupEntryView from "../../EntryViews/WorkingGroupEntryView";
import CommissionEntryView from "../../EntryViews/CommissionEntryView";
import UserContext from "../../UserContext";
import { useGuardianStyles } from "~/misc/utils";

interface SummaryComponentProps {
  projectName: string;
  fileName: string;
  isObituary: boolean;
  obituaryName?: string | null;
  projectTemplateId?: number;
  destinationStorageId?: number;
  workingGroupId?: number;
  productionOffice: string;
  commissionId?: number;
  deletable: boolean;
  deepArchive: boolean;
  sensitive: boolean;
}

const SummaryComponent: React.FC<SummaryComponentProps> = (props) => {
  const classes = useGuardianStyles();

  const userContext = useContext(UserContext);
  const cellGapStyle = {
    paddingRight: "0px",
    minWidth: "100px",
  };

  const italicTextStyle = {
    fontStyle: "italic",
  };

  const smallFontStyle = {
    fontSize: "0.75rem",
  };

  return (
    <Paper
      className={classes.common_box_size}
      style={{
        padding: "40px",
        overflowY: "auto",
        maxHeight: "90vh",
      }}
    >
      <Typography variant="h3" gutterBottom>
        New project review
      </Typography>
      <Typography style={{ fontSize: "0.75rem", marginBottom: "20px" }}>
        A new project will be created with the information below.
        <br />
        Press "Create" to proceed, or "Back" if you need to amend any details.
      </Typography>
      <Divider />

      <Grid container spacing={3} style={{ marginTop: "20px" }}>
        <Grid item xs={12} sm={6}>
          Pluto Project Name
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          {props.projectName ? (
            props.projectName
          ) : (
            <span style={{ color: "red" }}>Not provided</span>
          )}
        </Grid>
        <Grid item xs={12} sm={6}>
          File Name
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          {props.fileName ? (
            props.fileName
          ) : (
            <span style={{ color: "red" }}>Not provided</span>
          )}
        </Grid>

        {props.isObituary && (
          <Grid item xs={12} sm={6}>
            Obituary
          </Grid>
        )}
        {props.isObituary && (
          <Grid item xs={12} sm={6} style={italicTextStyle}>
            {props.obituaryName ? (
              props.obituaryName
            ) : (
              <span style={{ color: "red" }}>Not provided</span>
            )}
          </Grid>
        )}

        <Grid item xs={12} sm={6}>
          Project Template
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          {props.projectTemplateId ? (
            <ProjectTemplateEntryView entryId={props.projectTemplateId} />
          ) : (
            <span style={{ color: "red" }}>Not provided</span>
          )}
        </Grid>

        <Grid item xs={12} sm={6}>
          Working Group
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          {props.workingGroupId ? (
            <WorkingGroupEntryView entryId={props.workingGroupId} />
          ) : (
            <span style={{ color: "red" }}>Not provided</span>
          )}
        </Grid>

        <Grid item xs={12} sm={6}>
          Production Office
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          {props.productionOffice || (
            <span style={{ color: "red" }}>Not provided</span>
          )}
        </Grid>

        <Grid item xs={12} sm={6}>
          Commission
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          {props.commissionId ? (
            <CommissionEntryView entryId={props.commissionId} />
          ) : (
            <span style={{ color: "red" }}>Not provided</span>
          )}
        </Grid>

        <Grid item xs={12} sm={6}>
          Media Rules
        </Grid>

        {/* List of Media Rules */}
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          <Grid container spacing={3}>
            {props.deletable && <Grid item>Deletable</Grid>}
            {props.deepArchive && <Grid item>Deep Archive</Grid>}
            {props.sensitive && <Grid item>Sensitive</Grid>}
            {!props.deletable && !props.deepArchive && (
              <Grid item xs={12}>
                <Typography className={classes.error}>
                  You must select either deletable or deep archive
                </Typography>
              </Grid>
            )}
          </Grid>
        </Grid>
      </Grid>
    </Paper>
  );
};

export default SummaryComponent;
