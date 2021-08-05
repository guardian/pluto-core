import React, { CSSProperties, useContext } from "react";
import { VidispineItem } from "../mediabrowser-linkage/vidispine/item/VidispineItem";
import { makeStyles } from "@material-ui/core/styles";
import { Paper, Typography } from "@material-ui/core";
import { AcUnit } from "@material-ui/icons";
import { MediabrowserContext } from "../mediabrowser-linkage/mediabrowser";

interface AssetTileProps {
  item: VidispineItem;
  tileHeight: number;
  tileMargin: number;
  tileWidth: number;
  style?: CSSProperties;
}

const useStyles = makeStyles((theme) => ({
  caption: {
    overflow: "hidden",
    whiteSpace: "nowrap",
    textOverflow: "ellipsis",
    marginTop: "0.1em",
    marginBottom: "0.1em",
  },
  container: {
    width: "200px",
    height: "250px",
    overflow: "hidden",
    cursor: "pointer",
    padding: "10px",
    backgroundColor: theme.palette.type == "dark" ? "#222222" : "#DDDDDD",
  },
  image: {
    maxWidth: "95%",
    maxHeight: "90%",
    marginLeft: "auto",
    marginRight: "auto",
    display: "block",
    width: "240px",
    //margin: "-8px",
    marginTop: "0!important",
  },
}));

const AssetTile: React.FC<AssetTileProps> = (props) => {
  const classes = useStyles();

  const mediabrowserContext = useContext(MediabrowserContext);

  const getImageUrl = () => {
    if (!mediabrowserContext) {
      console.warn(
        "can't display image tile because media browser context is not set"
      );
    }

    const maybeValues = props.item.getMetadataValues("representativeThumbnail");
    if (maybeValues && maybeValues.length > 0 && mediabrowserContext) {
      return mediabrowserContext.vidispineBaseUrl + maybeValues[0];
    } else {
      return undefined;
    }
  };

  const maybeImageUrl = getImageUrl();

  const maybeTitle = props.item.getMetadataValues("title");
  const maybeCategory = props.item.getMetadataValues("gnm_category");

  return (
    <div style={props.style}>
      <Paper
        elevation={3}
        style={{ width: props.tileWidth, height: props.tileHeight }}
        className={classes.container}
      >
        <Typography className={classes.caption}>
          {maybeTitle && maybeTitle.length > 0 ? maybeTitle[0] : "(untitled)"}
        </Typography>
        <Typography className={classes.caption} style={{ fontSize: "0.8em" }}>
          {maybeCategory && maybeCategory.length > 0 ? maybeCategory[0] : ""}
        </Typography>
        {maybeImageUrl ? (
          <img
            src={maybeImageUrl}
            alt={props.item.id}
            className={classes.image}
          />
        ) : (
          <AcUnit />
        )}
      </Paper>
    </div>
  );
};

export default AssetTile;
