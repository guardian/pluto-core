import React, { useContext } from "react";
import { Divider, Grid, Paper, Typography } from "@material-ui/core";
import format from "date-fns/format";
import { isBefore, isAfter, addYears } from "date-fns";
import WorkingGroupEntryView from "../../EntryViews/WorkingGroupEntryView";
import UserContext from "../../UserContext";
import { useGuardianStyles } from "~/misc/utils";

interface SummaryComponentProps {
  title: string;
  scheduledCompetion: Date;
  workingGroupId?: number;
  productionOffice: ProductionOffice;
}

const SummaryComponent: React.FC<SummaryComponentProps> = (props) => {
  const classes = useGuardianStyles();

  const userContext = useContext(UserContext);
  const italicTextStyle = {
    fontStyle: "italic",
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
        New commission review
      </Typography>
      <Typography style={{ fontSize: "0.75rem", marginBottom: "20px" }}>
        A new commission will be created with the information below.
        <br />
        Press "Create" to proceed, or "Back" if you need to amend any details.
      </Typography>
      <Divider />

      <Grid container spacing={3} style={{ marginTop: "20px" }}>
        <Grid item xs={12} sm={4}>
          Working group
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          {props.workingGroupId ? (
            props.workingGroupId
          ) : (
            <span style={{ color: "red" }}>Not provided</span>
          )}
        </Grid>

        <Grid item xs={12} sm={4}>
          Commission name
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          <Typography id="title-value">
            {props.title ? (
              props.title
            ) : (
              <span style={{ color: "red" }}>Not provided</span>
            )}
          </Typography>
        </Grid>

        <Grid item xs={12} sm={4}>
          Scheduled completion
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          <Typography id="scheduled-completion-value">
            {props.scheduledCompetion &&
            !isBefore(props.scheduledCompetion, new Date()) &&
            !isAfter(props.scheduledCompetion, addYears(new Date(), 1)) ? (
              format(props.scheduledCompetion, "iiii, do MMM yyyy")
            ) : !props.scheduledCompetion ? (
              <span style={{ color: "red" }}>Not provided</span>
            ) : isBefore(props.scheduledCompetion, new Date()) ? (
              <span style={{ color: "red" }}>
                You can't create a commission with a completion date in the past
              </span>
            ) : isAfter(props.scheduledCompetion, addYears(new Date(), 1)) ? (
              <span style={{ color: "red" }}>
                This is a long way in the future, you should change it to
                something more realistic
              </span>
            ) : (
              <span style={{ color: "red" }}>Something went wrong</span>
            )}
          </Typography>
        </Grid>

        <Grid item xs={12} sm={4}>
          Production office
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          <Typography id="productionoffice-value">
            {props.productionOffice ? (
              props.productionOffice
            ) : (
              <span style={{ color: "red" }}>Not provided</span>
            )}
          </Typography>
        </Grid>

        <Grid item xs={12} sm={4}>
          Owner
        </Grid>
        <Grid item xs={12} sm={6} style={italicTextStyle}>
          {userContext?.userName ?? "not found"}
        </Grid>
      </Grid>
    </Paper>
  );
};

export default SummaryComponent;
