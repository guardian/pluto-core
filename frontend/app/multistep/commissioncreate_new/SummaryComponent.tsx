import React, { useContext } from "react";
import { Typography } from "@material-ui/core";
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
  return (
    <div className={classes.common_box_size}>
      <Typography variant="h2">Create a new commission</Typography>
      <Typography>
        We will create a new commission with the information below.
        <br />
        Press "Create" to go ahead, or Back if you need to amend any details.
      </Typography>

      <table>
        <tbody>
          <tr>
            <td>Working group</td>
            <td id="wg-value">
              {props.workingGroupId ? (
                <WorkingGroupEntryView entryId={props.workingGroupId} />
              ) : (
                "not set"
              )}
            </td>
            <td id="wg-error">
              {props.workingGroupId ? undefined : (
                <Typography className={classes.error}>
                  You must set a working group
                </Typography>
              )}
            </td>
          </tr>
          <tr>
            <td>Commission name</td>
            <td id="title-value">{props.title}</td>
            <td id="title-error">
              {props.title === "" ? (
                <Typography className={classes.error}>
                  You can't create a commission without a name
                </Typography>
              ) : undefined}
            </td>
          </tr>
          <tr>
            <td>Scheduled completion</td>
            <td id="scheduled-completion-value">
              {format(props.scheduledCompetion, "iiii, do MMM yyyy")}
            </td>
            <td id="scheduled-completion-err">
              {isBefore(props.scheduledCompetion, new Date()) ? (
                <Typography className={classes.error}>
                  You can't create a commission with a completion date in the
                  past
                </Typography>
              ) : undefined}
              {isAfter(props.scheduledCompetion, addYears(new Date(), 1)) ? (
                <Typography className={classes.error}>
                  This is a long way in the future, you should change it to
                  something more realistic
                </Typography>
              ) : undefined}
            </td>
          </tr>
          <tr>
            <td>Production office</td>
            <td id="productionoffice-value">{props.productionOffice}</td>
          </tr>
          <tr>
            <td>Owner</td>
            <td id="owner-value">{userContext?.userName ?? "not found"}</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
};

export default SummaryComponent;
