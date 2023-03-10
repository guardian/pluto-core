import React from "react";
import { useHistory } from "react-router-dom";
import { Helmet } from "react-helmet";
import { CheckCircle, ChevronRight } from "@material-ui/icons";
import { Button, Typography } from "@material-ui/core";
import { useGuardianStyles } from "~/misc/utils";

interface CommissionCreatedProps {
  commissionId: number;
  workingGroupId: number;
  title: string;
}

const CommissionCreated: React.FC<CommissionCreatedProps> = (props) => {
  const classes = useGuardianStyles();
  const history = useHistory();

  return (
    <div className={classes.container}>
      <Helmet>
        <title>Commission created - Pluto</title>
      </Helmet>
      <div style={{ marginLeft: "auto", marginRight: "auto", width: "100px" }}>
        <CheckCircle className={classes.success} />
      </div>

      <Typography className={classes.bannerText}>
        Your commission has been created.
        <br />
        Would you like to....
      </Typography>

      <table>
        <tbody>
          <tr>
            <td>
              <Typography>Create a project</Typography>
            </td>
            <td>
              <Button
                variant="contained"
                color="primary"
                endIcon={<ChevronRight />}
                onClick={() =>
                  history.push(
                    `/project/new?commissionId=${props.commissionId}&workingGroupId=${props.workingGroupId}`
                  )
                }
              >
                New Project
              </Button>
            </td>
          </tr>
          <tr>
            <td>
              <Typography>Go to the new commission's page</Typography>
            </td>
            <td>
              <Button
                endIcon={<ChevronRight />}
                variant="outlined"
                onClick={() =>
                  history.push(`/commission/${props.commissionId}`)
                }
              >
                Open Commission
              </Button>
            </td>
          </tr>
          <tr>
            <td>
              <Typography>Return to the commission list</Typography>
            </td>
            <td>
              <Button
                variant="outlined"
                endIcon={<ChevronRight />}
                onClick={() => history.push("/commission/")}
              >
                Commissions list
              </Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
};

export default CommissionCreated;
