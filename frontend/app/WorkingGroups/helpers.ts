import Axios from "axios";

const API = "/api/pluto";
const API_WORKING_GROUP = `${API}/workinggroup`;

interface WorkingGroupsOnPage {
  page?: number;
  pageSize?: number;
}

export const getWorkingGroupsOnPage = async ({
  page = 0,
  pageSize = 25,
}): Promise<WorkingGroup[]> => {
  try {
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<WorkingGroup[]>>(
      `${API_WORKING_GROUP}?startAt=${page * pageSize}&length=${pageSize}`
    );

    if (status === 200) {
      return result;
    }

    throw new Error(`Could not retrieve working groups. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getWorkingGroup = async (id: number): Promise<WorkingGroup> => {
  try {
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<WorkingGroup>>(
      `${API_WORKING_GROUP}/${id}`
    );

    if (status === 200) {
      return result;
    } else {
      throw new Error(`Could not retrieve working group id: ${id}.`);
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const createWorkingGroup = async (
  workingGroup: CreateWorkingGroup
): Promise<void> => {
  try {
    const { status } = await Axios.post<PlutoApiResponse<void>>(
      `${API_WORKING_GROUP}`,
      {
        ...workingGroup,
        hide: false,
      }
    );

    if (status !== 200) {
      throw new Error(`Could not create working group. ${status}`);
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};
