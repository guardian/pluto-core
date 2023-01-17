import axios from "axios";
import { SystemNotifcationKind, SystemNotification } from "@guardian/pluto-headers";

const loadWorkingGroups = async (
  setKnownWorkingGroups: (prevState: WorkingGroup[]) => void
) => {
  const response = await axios.get<PlutoApiResponse<WorkingGroup[]>>(
    `/api/pluto/workinggroup`,
    { validateStatus: () => true }
  );
  switch (response.status) {
    case 200:
      console.log("Loaded in ", response.data.result.length, " working groups");
      setKnownWorkingGroups(response.data.result.filter((wg) => !wg.hide));
      break;
    default:
      console.error(
        "Could not load in working groups, server said ",
        response.data
      );
      SystemNotification.open(
        SystemNotifcationKind.Error,
        "Server error loading working groups. Try refreshing your browser in a minute or two."
      );
      break;
  }
};

export { loadWorkingGroups };
