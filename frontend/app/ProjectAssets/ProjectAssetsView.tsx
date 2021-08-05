import React, { useContext, useEffect, useState } from "react";
import { FixedSizeGrid, GridChildComponentProps } from "react-window";
import { CircularProgress, Typography } from "@material-ui/core";
import { VidispineItem } from "../mediabrowser-linkage/vidispine/item/VidispineItem";
import { MediabrowserContext } from "../mediabrowser-linkage/mediabrowser";
import AssetTile from "./AssetTile";
import { assetsForProject } from "../mediabrowser-linkage/vidispine-service";
import { SystemNotification, SystemNotifcationKind } from "pluto-headers";
import { makeStyles } from "@material-ui/core/styles";

interface ProjectAssetsViewProps {
  projectid: number;
  totalCountUpdated?: (newValue: number | undefined) => void;
}

const useStyles = makeStyles((theme) => ({
  note: {
    fontSize: "0.8em",
    fontStyle: "italic",
    textAlign: "center",
  },
}));

const ProjectAssetsView: React.FC<ProjectAssetsViewProps> = (props) => {
  const [loading, setLoading] = useState(true);
  const [initialLoad, setInitialLoad] = useState(true);
  const [loadedAssetCount, setLoadedAssetCount] = useState(0);
  const [totalAssetCount, setTotalAssetCount] = useState<number | undefined>(
    undefined
  );

  const [projectAssets, setProjectAssets] = useState<VidispineItem[]>([]);

  const mediaBrowserContext = useContext(MediabrowserContext);
  const classes = useStyles();

  const tileHeight = 240;
  const tileWidth = 220;
  const tileMargin = 20;

  useEffect(() => {
    if (props.totalCountUpdated) props.totalCountUpdated(totalAssetCount);
  }, [totalAssetCount]);

  const reload = () => {
    if (mediaBrowserContext) {
      setInitialLoad(false);
      setLoading(true);
      assetsForProject(
        mediaBrowserContext.vidispineBaseUrl,
        props.projectid,
        1,
        30
      )
        .then((assets) => {
          setProjectAssets(assets.items);
          setLoadedAssetCount(assets.items.length);
          setTotalAssetCount(assets.totalCount);
          setLoading(false);
        })
        .catch((err) => {
          setLoading(false);
          console.error("could not load in assets: ", err);
          SystemNotification.open(
            SystemNotifcationKind.Error,
            "Can't display assets because " + err
          );
        });
    } else {
      console.error(
        "can't load project assets data as media browser context is not set"
      );
    }
  };

  useEffect(() => {
    if (initialLoad) {
      reload();
    }
  }, [mediaBrowserContext]);

  const renderTile = (props: GridChildComponentProps<{}>) => {
    const { columnIndex, rowIndex, style } = props;
    const index = columnIndex; //we only have one row

    return index >= loadedAssetCount ? (
      <></>
    ) : (
      <AssetTile
        key={index}
        item={projectAssets[index]}
        style={style}
        tileHeight={tileHeight}
        tileWidth={tileWidth}
        tileMargin={tileMargin}
      />
    );
  };
  return loading ? (
    <div style={{ height: tileHeight + tileMargin, overflow: "hidden" }}>
      <CircularProgress
        style={{ marginTop: (tileHeight + tileMargin) / 2 - 20 }}
      />
    </div>
  ) : totalAssetCount ? (
    <FixedSizeGrid
      columnWidth={tileWidth + tileMargin}
      rowHeight={tileHeight + tileMargin}
      columnCount={loadedAssetCount}
      height={tileHeight + tileMargin}
      rowCount={1}
      width={(tileWidth + tileMargin) * loadedAssetCount}
    >
      {renderTile}
    </FixedSizeGrid>
  ) : (
    <Typography className={classes.note}>
      There are no online media items registered with this project, this could
      be because it has not yet been scanned or because it has been archived
    </Typography>
  );
};

export default ProjectAssetsView;
