import Axios from "axios";

const API = "/api/project";
const API_DELETED = `${API}/deleted`;

interface DeletionRecordsOnPage {
  page?: number;
  pageSize?: number;
}

export const getDeletionRecordsOnPage = async ({
  page = 0,
  pageSize = 25,
}): Promise<DeletionRecord[]> => {
  try {
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<DeletionRecord[]>>(
      `${API_DELETED}?startAt=${page * pageSize}&length=${pageSize}`
    );

    if (status === 200) {
      console.log(result);
      return result;
    }

    throw new Error(`Could not retrieve deletion records. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};
