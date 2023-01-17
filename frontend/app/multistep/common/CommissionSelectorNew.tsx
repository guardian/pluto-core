import React, { useEffect, useState } from "react";
import axios from "axios";
import { SystemNotification, SystemNotifcationKind } from "@guardian/pluto-headers";
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
import { useGuardianStyles } from "~/misc/utils";

interface CommissionSelectorProps {
  valueWasSet: (newValue: number | undefined) => void;
  workingGroupId?: number;
  selectedCommissionId?: number;
  showStatus?: ProjectStatus | "all";
}

const CommissionSelector: React.FC<CommissionSelectorProps> = (props) => {
  const [searchText, setSearchText] = useState("");
  const [seenCommissions, setSeenCommissions] = useState<Commission[]>([]);
  const [totalResultCount, setTotalResultCount] = useState(0);
  const [loading, setLoading] = useState(false);

  const classes = useGuardianStyles();

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
          SystemNotifcationKind.Error,
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

  /**
   * if the commission id has been set externally, validate that it is in fact part of this working group
   */
  useEffect(() => {
    if (seenCommissions.length > 0 && props.selectedCommissionId) {
      const matches = seenCommissions.filter(
        (comm) => comm.id == props.selectedCommissionId
      );
      if (matches.length == 0) {
        console.log("selected commission id was not found, removing it");
        props.valueWasSet(undefined);
      }
    }
  }, [seenCommissions, props.selectedCommissionId]);

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
    <Grid justifyContent="space-between" container direction="column">
      <Grid item>
        <Search className={classes.commissionSelectorinlineIcon} />
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
