import React from "react";
import {
  Checkbox,
  FormControlLabel,
  Grid,
  Radio,
  Typography,
} from "@material-ui/core";
import { makeStyles } from "@material-ui/core/styles";

interface MediaRulesComponentProps {
  deletable: boolean;
  deepArchive: boolean;
  sensitive: boolean;
  archivalChanged: (deletable: boolean, deepArchive: boolean) => void;
  sensitiveChanged: (newValue: boolean) => void;
}

const useStyles = makeStyles((theme) => ({
  secondaryText: {
    color: theme.palette.text.secondary,
    fontSize: theme.typography.body2.fontSize,
  },
}));

const MediaRulesComponent: React.FC<MediaRulesComponentProps> = (props) => {
  const deepArchiveSelected = () => {
    props.archivalChanged(false, true);
  };

  const deletableSelected = () => {
    props.archivalChanged(true, false);
  };

  const classes = useStyles();

  return (
    <div>
      <Typography variant="h3">Media Management Rules</Typography>
      <Typography>
        We need to know which archive management rules to apply to this
        project's media.
      </Typography>
      <Grid container direction="column">
        <Grid item>
          <FormControlLabel
            label="Keep media forever"
            control={
              <Radio
                checked={props.deepArchive}
                onClick={() => deepArchiveSelected()}
              />
            }
          />
          <Typography className={classes.secondaryText}>
            The project and its assets will be archived long term externally
            after the project is marked as complete.
          </Typography>
        </Grid>
        <Grid item>
          <FormControlLabel
            label="Media can be removed once the project has been completed"
            control={
              <Radio
                checked={props.deletable}
                onClick={() => deletableSelected()}
              />
            }
          />
          <Typography className={classes.secondaryText}>
            The project and its assets can be deleted after completion (make
            sure all project deliverables are done before marking the project as
            complete)
          </Typography>
        </Grid>
        <Grid item>
          <FormControlLabel
            label="Media should not leave the building"
            control={
              <Checkbox
                checked={props.sensitive}
                onChange={(evt) => props.sensitiveChanged(evt.target.checked)}
              />
            }
          />
          <Typography className={classes.secondaryText}>
            The project will contain sensitive content and all assets will be
            kept and archived within the Guardian
          </Typography>
        </Grid>
      </Grid>
    </div>
  );
};

export default MediaRulesComponent;
