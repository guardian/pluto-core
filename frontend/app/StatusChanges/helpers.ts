import Axios from "axios";

const API = "/api";
const API_STATUS = `${API}/statusChanges`;

interface StatusChangesOnPage {
  page?: number;
  pageSize?: number;
}

export const getStatusChangesOnPage = async ({
  page = 0,
  pageSize = 10,
}): Promise<StatusChange[]> => {
  try {
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<StatusChange[]>>(
      `${API_STATUS}?startAt=${page * pageSize}&length=${pageSize}`
    );

    if (status === 200) {
      console.log(result);
      return result;
    }

    throw new Error(`Could not retrieve status changes. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};
