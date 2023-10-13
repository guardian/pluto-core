import { makeStyles } from "@material-ui/core/styles";

/**
 * Collection of reusable styles.
 *
 * Usage:
 * import { useGuardianStyles } from "~/misc/utils";
 * const classes = useGuardianStyles();
 *
 * <Grid className={classes.infoBanner} />
 */

export const useGuardianStyles = makeStyles((theme) => ({
  // Shared
  root: {
    display: "flex",
    flexDirection: "column",
    justifyContent: "space-between",
    width: "100%",
    padding: "1rem",
    "& .MuiTextField-root": {
      width: "100%",
      marginBottom: "1rem",
    },
    "& .MuiFormControl-root": {
      width: "100%",
      marginBottom: "1rem",
      padding: "20",
    },
    "& form": {
      padding: "0.6em",
      display: "flex",
      flexDirection: "column",
      alignItems: "flex-start",
      margin: 0,
    },
  },
  container: {
    padding: "1em",
  },
  table: {
    minWidth: 750,
    maxWidth: "100%",
    "& .MuiTableRow-root": {
      cursor: "pointer",
    },
  },
  buttonGrid: {
    marginLeft: "auto",
  },
  visuallyHidden: {
    border: 0,
    clip: "rect(0 0 0 0)",
    height: 1,
    margin: -1,
    overflow: "hidden",
    padding: 0,
    position: "absolute",
    top: 20,
    width: 1,
  },
  warningText: {
    color: theme.palette.warning.dark,
    textAlign: "center",
  },
  bannerText: {
    fontWeight: theme.typography.fontWeightBold,
    textAlign: "center",
    marginBottom: "1em",
  },
  error: {
    color: theme.palette.error.dark,
  },
  warning: {
    color: theme.palette.warning.dark,
  },
  success: {
    color: theme.palette.success.dark,
  },
  loading: {
    display: "flex",
    flexDirection: "column",
    width: "100%",
    alignItems: "center",
  },
  selectedItem: {
    backgroundColor: theme.palette.action.selected,
  },
  inputBox: {
    width: "50vw",
    minWidth: "100px",
    maxWidth: "600px",
  },
  secondary: {
    fontSize: theme.typography.body2.fontSize,
    color: theme.palette.text.secondary,
  },
  selectorbox: {
    width: "50%",
  },
  successBlock: {
    color: theme.palette.success.dark,
    width: "100px",
    height: "100px",
  },
  formButtons: {
    width: "100%",
    display: "flex",
    marginTop: "2.5rem",
    justifyContent: "flex-end",
    "& Button": {
      marginLeft: "1rem",
    },
  },
  warningIcon: {
    color: theme.palette.warning.main,
    marginLeft: "10px",
  },
  createButton: {
    display: "flex",
    marginBottom: "0.625rem",
  },
  openProjectButton: {
    whiteSpace: "nowrap",
  },
  //MaterialUITable.jsx
  paper: {
    width: "100%",
    marginBottom: theme.spacing(2),
  },
  paperWithPadding: {
    "&&": {
      marginBottom: "12px",
      minHeight: "120px",
      height: "115px",
      width: "100%",
      padding: "0px 0px 0px 20px",
    },
  },
  //ChipsWithWarning.tsx
  //CommissionEntryDeliverablesComponent.tsx
  //CommissionEntryEditComponent.tsx
  inlineThrobber: {
    marginRight: "0.6em",
    maxWidth: "28px",
    maxHeight: "28px",
  },
  inlineText: {
    display: "inline",
  },
  invisibleList: {
    listStyle: "none",
  },
  errorBlock: {
    backgroundColor: "rgb(211 47 47)",
    padding: "10px",
    color: "#FFF",
  },
  noGoogleText: {
    float: "left",
    marginTop: "2px",
  },
  //CommissionList.tsx
  //WorkingGroupSelector.tsx
  discontinuedWG: {
    fontStyle: "italic",
    color: "darkgrey",
  },
  normalWG: {},
  validationError: {
    color: theme.palette.error.dark,
  },
  //CommissionCreated.tsx
  //CommissionTitleComponent.tsx
  //PlutoLinkageComponent.tsx
  //SummaryComponent.tsx
  //CommissionSelectorNew.tsx
  commissionSelectorinlineIcon: {
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
  //WorkingGroupSelectorNew.tsx
  //InProgressComponent.tsx
  centeredContainer: {
    marginLeft: "auto",
    marginRight: "auto",
    width: "400px",
    marginTop: "auto",
    marginBottom: "auto",
    padding: "1em",
  },
  errorIcon: {
    color: theme.palette.warning.dark,
    width: "100px",
    height: "100px",
  },
  regularText: {
    textAlign: "center",
  },
  progressSpinner: {
    width: "100px",
    height: "100px",
  },
  //MediaRulesComponent.tsx
  secondaryText: {
    color: theme.palette.text.secondary,
    fontSize: theme.typography.body2.fontSize,
  },
  //NameComponent.tsx
  //PlutoLinkageComponent.tsx
  //ProjectCreatedComponent.tsx
  //SummaryComponent.tsx
  //BackupsComponent.tsx
  greyed: {
    color: theme.palette.text.disabled,
  },
  normal: {
    color: theme.palette.text.primary,
  },
  //PremiereVersionChange.tsx
  centered: {
    marginTop: "0.4em",
    textAlign: "center",
  },
  buttonContainer: {
    marginTop: "1em",
    maxWidth: "800px",
    marginLeft: "auto",
    marginRight: "auto",
  },
  //VersionTranslationRow.tsx
  editField: {
    width: "100%",
  },
  //VersionTranslationList.tsx
  iconBanner: {
    width: "100px",
    marginLeft: "auto",
  },
  //BackupEntry.tsx
  emphasis: {
    fontWeight: theme.typography.fontWeightBold,
  },
  //ProjectBackups.tsx
  inlineIcon: {
    marginRight: "6px",
    verticalAlign: "top",
  },
  centeredDiv: {
    paddingTop: "2em",
    paddingBottom: "2em",
    justifyContent: "space-around",
  },
  emphasised: {
    fontWeight: theme.typography.fontWeightBold,
  },
  noSpacing: {
    marginBottom: "0",
  },
  //ProjectEntryDeliverablesComponent.tsx
  projectDeliverable: {
    display: "flex",
    flexDirection: "column",
    padding: "1rem",
    marginTop: "1rem",

    "& .MuiTypography-subtitle1": {
      marginTop: "6px",
      marginBottom: "6px",
    },
    "& .error": {
      backgroundColor: "rgb(211 47 47)",
      padding: "10px",
      color: "#FFF",
      "& .content": {
        display: "flex",
        alignItems: "center",

        "& .message": {
          marginLeft: "6px",
        },
      },
    },
    "& .button-container": {
      marginTop: "1rem",
    },
  },
  //ProjectEntryEditComponent.tsx
  applicableRules: {
    display: "flex",
    flexDirection: "column",
  },
  // ProjectEntryList.tsx
  //ProjectEntryVaultComponent.tsx
  projectVaultData: {
    display: "flex",
    flexDirection: "column",
    padding: "1rem",
    marginTop: "1rem",
  },
  archiveButton: {
    width: "50px",
    height: "50px",
  },
  archiveIcon: {
    width: "60px",
    height: "60px",
  },
  //ProjectsTable.tsx
  //ValidationJobResults.tsx
  headerTitle: {
    fontWeight: "bold",
    fontSize: "1.1rem",
  },
  resultsTable: {
    maxHeight: "60vh",
  },
  infoBanner: {
    marginTop: "1em",
    marginBottom: "1em",
  },
  fullWidth: {
    width: "100%",
  },
  //ValidationJobRow.tsx
  tableRow: {
    cursor: "pointer",
  },
  //ValidationJobsTable.tsx
  tableHeaderText: {
    fontSize: "0.8em",
  },
  //WorkingGroup.tsx
  hide_control: {
    marginBottom: "20px",
  },
  //WorkingGroups.tsx
  createNewWorkingGroup: {
    display: "flex",
    marginLeft: "auto",
    marginBottom: "0.625rem",
  },
  visibilityIcon: {},
  //CommonMultiStepContainer.tsx
  stepContainer: {
    width: "fit-content",
    padding: "3em",
    paddingTop: "0.5em",
    paddingBottom: "1em",
    marginLeft: "auto",
    marginRight: "auto",
    marginTop: "3em",
  },
  information: {
    color: theme.palette.info.main,
    fontSize: "0.8em",
    fontStyle: "italic",
  },
  labelCell: {
    verticalAlign: "bottom",
    width: "25%",
  },
  valueNotPresent: {
    color: theme.palette.grey.A700,
  },
  stepper: {
    backgroundColor: "#00000000",
  },
  //VersionTranslationRow.tsx
  //VersionTranslationList.tsx
  //SubfolderComponent.tsx
  subfolderTable: {
    maxWidth: "90vw",
    minWidth: "33vw",
    width: "800px",
  },
  //UsersAutoComplete.tsx
  obituaryButton: {
    margin: 0,
    padding: 0,
    border: 0,
    fontWeight: theme.typography.fontWeightBold,
    letterSpacing: "initial",
    textTransform: "none",
    fontSize: "12px",
    color: theme.palette.text.primary,
    textDecoration: "underline",
    "&::hover": {
      color: theme.palette.text.secondary,
    },
  },
  obituariesTitle: {
    fontSize: "2.5em",
  },
  title_case_text: {
    textTransform: "capitalize",
  },
  common_box_size: {
    width: "900px",
    height: "570px",
  },
}));
