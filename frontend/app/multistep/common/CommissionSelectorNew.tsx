import React, { useEffect, useState } from "react";
import axios from "axios";
import SystemNotification, {
  SystemNotificationKind,
} from "../../SystemNotification";
import {
  CircularProgress,
  Grid,
  IconButton,
  Input,
  ListItem,
  ListItemText,
  Typography,
} from "@material-ui/core";
import { Cancel, Search } from "@material-ui/icons";
import { FixedSizeList, ListChildComponentProps } from "react-window";
import { makeStyles } from "@material-ui/core/styles";

/*
valueWasSet={props.commissionIdDidChange}
                                        workingGroupId={props.workingGroupId}
                                        selectedCommissionId={props.commissionId}
                                        showStatus={showingStatus}
 */
interface CommissionSelectorProps {
  valueWasSet: (newValue: number) => void;
  workingGroupId?: number;
  selectedCommissionId?: number;
  showStatus?: ProjectStatus | "all";
}

const useStyles = makeStyles((theme) => ({
  selectedItem: {
    backgroundColor: theme.palette.action.selected,
  },
  inlineIcon: {
    marginRight: theme.spacing(1),
  },
  textInput: {
    verticalAlign: "top",
    marginRight: theme.spacing(1),
    width: "70%",
  },
  cancelButton: {
    color: theme.palette.grey.A700,
  },
  inlineProgressMeter: {
    marginRight: theme.spacing(1),
    marginLeft: theme.spacing(1),
    height: "1em",
  },
  warningText: {
    color: theme.palette.warning.dark,
    textAlign: "center",
  },
}));

const CommissionSelector: React.FC<CommissionSelectorProps> = (props) => {
  const [searchText, setSearchText] = useState("");
  const [seenCommissions, setSeenCommissions] = useState<Commission[]>([]);
  const [totalResultCount, setTotalResultCount] = useState(0);
  const [loading, setLoading] = useState(false);

  const classes = useStyles();

  const makeSearchDoc = () => {
    return {
      title: searchText == "" ? undefined : searchText,
      workingGroupId: props.workingGroupId,
      status: props.showStatus == "all" ? undefined : props.showStatus,
      match: "W_CONTAINS",
      showKilled: false,
    };
  };

  const updateResults = async () => {
    setLoading(true);
    const response = await axios.put<ObjectListResponse<Commission>>(
      "/api/pluto/commission/list?length=50",
      makeSearchDoc(),
      { validateStatus: () => true }
    );
    setLoading(false);
    switch (response.status) {
      case 200:
        setTotalResultCount(response.data.count);
        setSeenCommissions(response.data.result);
        break;
      default:
        console.error(
          "Could not load in commissions for ",
          makeSearchDoc(),
          ": ",
          response.status,
          response.data
        );
        SystemNotification.open(
          SystemNotificationKind.Error,
          "Could not load in commissions, see browser console for details"
        );
        break;
    }
  };

  useEffect(() => {
    const timerId = window.setTimeout(() => updateResults(), 1000);
    return () => {
      window.clearTimeout(timerId);
    };
  }, [searchText]);

  useEffect(() => {
    updateResults();
  }, [props.showStatus]);

  useEffect(() => {
    setSeenCommissions([]);
    setTotalResultCount(0);
    updateResults();
  }, [props.workingGroupId]);

  const renderRow = (rowProps: ListChildComponentProps) => {
    if (rowProps.index > seenCommissions.length) return null;
    const entry = seenCommissions[rowProps.index];

    if (entry) {
      return (
        <ListItem
          button
          style={rowProps.style}
          onClick={() => props.valueWasSet(entry.id)}
          className={
            entry.id === props.selectedCommissionId
              ? classes.selectedItem
              : undefined
          }
        >
          <ListItemText primary={entry.title} secondary={`${entry.status}`} />
        </ListItem>
      );
    } else {
      return (
        <ListItem style={rowProps.style}>
          <ListItemText
            primary="Invalid"
            secondary={`Null data entry for ${rowProps.index}`}
          />
        </ListItem>
      );
    }
  };
  return (
    <Grid container direction="column">
      <Grid item>
        <Search className={classes.inlineIcon} />
        <Input
          value={searchText}
          className={classes.textInput}
          onChange={(evt) => setSearchText(evt.target.value)}
        />
        {searchText == "" ? null : (
          <IconButton onClick={() => setSearchText("")}>
            <Cancel className={classes.cancelButton} />
          </IconButton>
        )}
        {loading ? (
          <CircularProgress className={classes.inlineProgressMeter} />
        ) : undefined}
      </Grid>
      <Grid item>
        {totalResultCount > 0 ? (
          <FixedSizeList
            itemSize={50}
            height={300}
            itemCount={totalResultCount}
            width={400}
          >
            {renderRow}
          </FixedSizeList>
        ) : (
          <Typography className={classes.warningText}>
            No commissions seen
          </Typography>
        )}
      </Grid>
    </Grid>
  );
};

export default CommissionSelector;
