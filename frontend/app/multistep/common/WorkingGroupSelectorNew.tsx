import React, { useState } from "react";
import { makeStyles } from "@material-ui/core/styles";
import {
  Grid,
  Input,
  ListItem,
  ListItemText,
  Typography,
} from "@material-ui/core";
import { Search } from "@material-ui/icons";
import { FixedSizeList, ListChildComponentProps } from "react-window";

interface WorkingGroupSelectorProps {
  valueWasSet: (newValue: number) => void;
  workingGroupList: WorkingGroup[];
  currentValue: number | undefined;
}

const useStyles = makeStyles((theme) => ({
  selectedItem: {
    backgroundColor: theme.palette.action.selected,
  },
}));

const WorkingGroupSelector: React.FC<WorkingGroupSelectorProps> = (props) => {
  const [searchText, setSearchText] = useState("");
  const classes = useStyles();

  const renderRow = (rowProps: ListChildComponentProps) => {
    if (rowProps.index > props.workingGroupList.length) return null;
    const entry = props.workingGroupList[rowProps.index];

    return searchText == "" ||
      entry.name.toLowerCase().includes(searchText.toLowerCase()) ? (
      <ListItem
        button
        style={rowProps.style}
        onClick={() => props.valueWasSet(entry.id)}
        className={
          entry.id === props.currentValue ? classes.selectedItem : undefined
        }
      >
        <ListItemText primary={entry.name} secondary={entry.commissioner} />
      </ListItem>
    ) : null;
  };

  return (
    <Grid container direction="column">
      <Grid item>
        <Search />
        <Input
          value={searchText}
          onChange={(evt) => setSearchText(evt.target.value)}
        />
      </Grid>
      <Grid>
        {props.workingGroupList.length > 0 ? (
          <FixedSizeList
            itemSize={50}
            height={300}
            itemCount={props.workingGroupList.length}
            width={400}
          >
            {renderRow}
          </FixedSizeList>
        ) : (
          <Typography>No working groups loaded</Typography>
        )}
      </Grid>
    </Grid>
  );
};

export default WorkingGroupSelector;
