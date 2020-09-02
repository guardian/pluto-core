import axios from "axios";

const API = "/api";
const API_IS_LOGGED_IN = `${API}/isLoggedIn`;
const API_DELIVERABLES = `/deliverables${API}/deliverables`;

export const isLoggedIn = async (): Promise<PlutoUser> => {
  try {
    const { status, data } = await axios.get<PlutoUser>(`${API_IS_LOGGED_IN}`);

    if (status === 200) {
      return data;
    }

    throw new Error(`Could not retrieve who is logged in. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getProjectDeliverables = async (
  projectId: number
): Promise<Deliverable[]> => {
  try {
    const { status, data } = await axios.get<Deliverable[]>(
      `${API_DELIVERABLES}?project_id=${projectId}`
    );

    if (status === 200) {
      return data;
    }

    throw new Error(`Could not fetch Project deliverables. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};
