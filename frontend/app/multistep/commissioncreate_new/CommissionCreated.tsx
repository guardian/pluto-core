import React from "react";
import { useHistory } from "react-router-dom";
import { Helmet } from "react-helmet";
import { CheckCircle, ChevronRight } from "@material-ui/icons";
import { Button, Grid, Typography } from "@material-ui/core";
import { useGuardianStyles } from "~/misc/utils";

interface CommissionCreatedProps {
  commissionId: number;
  workingGroupId: number;
  title: string;
}

const CommissionCreated: React.FC<CommissionCreatedProps> = (props) => {
  const classes = useGuardianStyles();
  const history = useHistory();

  const buttonStyle = {
    minWidth: "175px",
    minHeight: "50px",
  };

  return (
    <div className={classes.container}>
      <Helmet>
        <title>Commission created - Pluto</title>
      </Helmet>
      <div
        style={{
          display: "flex",
          flexDirection: "row",
          justifyContent: "center",
          marginBottom: "50px",
        }}
      >
        <CheckCircle className={classes.success} style={{ width: "80px" }} />
        <Typography className={classes.bannerText} gutterBottom>
          Your commission has been created! <br />
        </Typography>
      </div>
      <br />
      <Grid container spacing={3}>
        <Grid item xs={8}>
          <Typography>Create a project</Typography>
        </Grid>
        <Grid item xs={4}>
          <Button
            style={buttonStyle}
            color="primary"
            variant="contained"
            onClick={() =>
              history.push(
                `/project/new?commissionId=${props.commissionId}&workingGroupId=${props.workingGroupId}`
              )
            }
          >
            New Project
          </Button>
        </Grid>
        <Grid item xs={8}>
          <Typography>Go to the new commission's page</Typography>
        </Grid>
        <Grid item xs={4}>
          <Button
            style={buttonStyle}
            variant="outlined"
            onClick={() => history.push(`/commission/${props.commissionId}`)}
          >
            Open Commission
          </Button>
        </Grid>
        <Grid item xs={8}>
          <Typography>Return to the commission list</Typography>
        </Grid>
        <Grid item xs={4}>
          <Button
            style={buttonStyle}
            variant="outlined"
            onClick={() => history.push("/commission/")}
          >
            Commissions list
          </Button>
        </Grid>
      </Grid>
    </div>
  );
};

export default CommissionCreated;
