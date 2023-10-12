import React, { useContext } from "react";
import { Grid, Paper, Typography } from "@material-ui/core";
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
    paddingRight: "100px",
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
      style={{ padding: "40px", display: "flex", flexDirection: "column" }}
    >
      <Typography variant="h3">Create new edit project</Typography>
      <Typography style={smallFontStyle}>
        A new project will be created with the information below.
        <br />
        Press "Create" to proceed, or "Back" if you need to amend any details.
      </Typography>
      <br />

      <table style={{ maxWidth: "560px" }}>
        <tbody>
          <tr>
            <td style={cellGapStyle}>Pluto Project Name</td>
            <td>{"⟶"}</td>
            <td style={italicTextStyle}>{props.projectName}</td>
            <td>
              {props.projectName == "" ? (
                <Typography className={classes.warning}>
                  We can't create a project without a name
                </Typography>
              ) : null}
            </td>
            <td>
              {props.projectName == "My project" ||
              props.projectName == `${userContext?.userName}'s project` ? (
                <Typography className={classes.warning}>
                  Don't be ridiculous, you need to give the project a
                  descriptive name that means someone can find it in the future!
                </Typography>
              ) : undefined}
            </td>
          </tr>
          <tr>
            <td style={cellGapStyle}>File Name</td>
            <td>{"⟶"}</td>
            <td style={italicTextStyle}>{props.fileName}</td>
          </tr>
          {props.isObituary && (
            <>
              {props.obituaryName && (
                <tr>
                  <td>Obituary</td>
                  <td>{"⟶"}</td>
                  <td className={classes.title_case_text}>
                    {props.obituaryName}
                  </td>
                </tr>
              )}
              {!props.obituaryName && (
                <tr>
                  <td style={cellGapStyle}>Obituary</td>
                  <td>
                    <Typography className={classes.warning}>
                      You need to pick a name for the obituary.
                    </Typography>
                  </td>
                </tr>
              )}
            </>
          )}
          <tr>
            <td style={cellGapStyle}>Project Template</td>
            <td>{"⟶"}</td>
            <td style={italicTextStyle}>
              {props.projectTemplateId ? (
                <ProjectTemplateEntryView entryId={props.projectTemplateId} />
              ) : (
                <Typography className={classes.error}>
                  We need a template to initialise the project
                </Typography>
              )}
            </td>
          </tr>
          <tr>
            <td style={cellGapStyle}>Working Group</td>
            <td>{"⟶"}</td>
            <td style={italicTextStyle}>
              {props.workingGroupId ? (
                <WorkingGroupEntryView entryId={props.workingGroupId} />
              ) : (
                <Typography className={classes.warning}>
                  You should go back and select a Working Group
                </Typography>
              )}
            </td>
          </tr>
          <tr>
            <td style={cellGapStyle}>Production Office</td>
            <td>{"⟶"}</td>
            <td style={italicTextStyle}>{props.productionOffice}</td>
          </tr>
          <tr>
            <td style={cellGapStyle}>Commission</td>
            <td>{"⟶"}</td>
            <td style={italicTextStyle}>
              {props.commissionId ? (
                <CommissionEntryView entryId={props.commissionId} />
              ) : (
                <Typography className={classes.warning}>
                  You should go back and select a Commission
                </Typography>
              )}
            </td>
          </tr>
          <tr>
            <td style={cellGapStyle}>Media Rules</td>
            <td>{"⟶"}</td>
            <td style={italicTextStyle}>
              <Grid container spacing={3} direction="row">
                {props.deletable ? (
                  <Grid item>
                    <Typography>Deletable</Typography>
                  </Grid>
                ) : undefined}
                {props.deepArchive ? (
                  <Grid item>
                    <Typography>Deep Archive</Typography>
                  </Grid>
                ) : undefined}
                {props.sensitive ? (
                  <Grid item>
                    <Typography>Sensitive</Typography>
                  </Grid>
                ) : undefined}
                {!props.deletable && !props.deepArchive ? (
                  <Grid item>
                    <Typography className={classes.error}>
                      You must select either deletable or deep archive
                    </Typography>
                  </Grid>
                ) : undefined}
              </Grid>
            </td>
          </tr>
        </tbody>
      </table>
    </Paper>
  );
};

export default SummaryComponent;
