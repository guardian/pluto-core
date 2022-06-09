import React, { useEffect, useState } from "react";
import {
  CircularProgress,
  FormControlLabel,
  Grid,
  Radio,
  Typography,
} from "@material-ui/core";
import StorageSelector from "../../Selectors/StorageSelector";
import axios from "axios";
import { SystemNotification, SystemNotifcationKind } from "pluto-headers";
import { useGuardianStyles } from "~/misc/utils";

interface BackupsComponentProps {
  selectedBackupStorage?: number;
  onChange: (newValue?: number) => void;
  existingStorageId?: number;
}

const BackupsComponent: React.FC<BackupsComponentProps> = (props) => {
  const [backupSelected, setBackupSelected] = useState(false);
  const [knownStorages, setKnownStorages] = useState<StorageEntry[]>([]);
  const [selectedStorage, setSelectedStorage] = useState(0);

  const [loading, setLoading] = useState(true);

  const classes = useGuardianStyles();

  useEffect(() => {
    const loadData = async () => {
      const response = await axios.get<ObjectListResponse<StorageEntry>>(
        "/api/storage"
      );
      setLoading(false);
      //if we are editing an existing storage, don't show that as an option for backups
      const storagesToSet = response.data.result.filter(
        (entry) => entry.id !== props.existingStorageId
      );

      setKnownStorages(storagesToSet);
      if (storagesToSet.length > 0) {
        //only change the selected storage if we did not have a value set from the parent already
        setSelectedStorage((prevState) =>
          prevState == 0 ? response.data.result[0].id : prevState
        );
      }
    };

    if (props.selectedBackupStorage != undefined) {
      setSelectedStorage(props.selectedBackupStorage);
      setBackupSelected(true);
    }

    loadData().catch((err) => {
      console.error("Could not load in storages: ", err);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        "Could not load in all storages, see console"
      );
    });
  }, []);

  useEffect(() => {
    if (!backupSelected) {
      props.onChange(undefined);
    } else {
      props.onChange(selectedStorage);
    }
  }, [backupSelected, selectedStorage]);

  return (
    <>
      <Typography variant="h3">Backups</Typography>
      <Typography>
        If this storage is to be used to store "live" data that can be edited by
        users, it's recommended to configure it to backup to a versioned
        storage.
      </Typography>
      <Typography>
        Selecting a <i>versioned</i> storage will ensure that a new version is
        created every time a project changes rather than the over-writing the
        previous backup file.
      </Typography>

      <Grid container direction="column" style={{ marginTop: "2em" }}>
        <Grid item>
          <FormControlLabel
            label="Don't back up this storage"
            control={
              <Radio
                checked={!backupSelected}
                onClick={() => setBackupSelected(false)}
              />
            }
          />
        </Grid>
        <Grid item>
          <FormControlLabel
            label="Back up this storage"
            control={
              <Radio
                checked={backupSelected}
                onClick={() => setBackupSelected(true)}
              />
            }
          />
          <Typography
            className={backupSelected ? classes.normal : classes.greyed}
          >
            Select a storage that will receive backup copies of data from this
            one
          </Typography>
          {loading ? (
            <CircularProgress />
          ) : (
            <StorageSelector
              selectionUpdated={(newValue: number) =>
                setSelectedStorage(newValue)
              }
              storageList={knownStorages}
              enabled={backupSelected}
              selectedStorage={selectedStorage}
            />
          )}
        </Grid>
      </Grid>
    </>
  );
};

export default BackupsComponent;
