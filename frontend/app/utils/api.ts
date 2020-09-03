import axios from "axios";
import Cookies from "js-cookie";

const API = "/api";
const API_IS_LOGGED_IN = `${API}/isLoggedIn`;
const API_DELIVERABLES = `/deliverables${API}`;

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
    const response = await axios.get<Deliverable[]>(
      `${API_DELIVERABLES}/deliverables?project_id=${projectId}`
    );

    if (response?.status === 200) {
      return response?.data;
    }

    if (response?.status === 404) {
      throw "";
    }

    throw "Could not fetch Project deliverables";
  } catch (error) {
    if (error?.response?.status === 404) {
      return Promise.reject();
    }
    console.error(error);
    return Promise.reject(error);
  }
};

export const createProjectDeliverable = async (
  project: Project
): Promise<Deliverable> => {
  const csrftoken = Cookies.get("csrftoken");
  if (!csrftoken) {
    console.warn("Could not find a csrf token! Request will probably fail");
  }

  try {
    const response = await axios.post<Deliverable>(
      `${API_DELIVERABLES}/bundle/new`,
      {
        pluto_core_project_id: project.id,
        commission_id: project.commissionId,
        name: project.title,
      },
      {
        headers: {
          "X-CSRFToken": csrftoken,
        },
      }
    );

    if (response?.status == 200) {
      return response?.data;
    }

    if (response?.status === 404) {
      throw "";
    }

    throw "Could not create Project deliverable";
  } catch (error) {
    if (error?.response?.status === 404) {
      return Promise.reject();
    }
    console.error(error);
    return Promise.reject(error);
  }
};
