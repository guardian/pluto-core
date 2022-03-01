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

export const getProjectDeliverableSummary = async (
  projectId: number
): Promise<DeliverablesCount | null> => {
  try {
    const response = await axios.get<DeliverablesCount>(
      `${API_DELIVERABLES}/bundle/${projectId}/count`
    );
    return response.data;
  } catch (err) {
    if (err.response && err.response.status == 404) {
      console.info(`Project ${projectId} has no deliverable bundle`);
      return null;
    }
    console.error("Could not load deliverable summary: ", err);
    throw err;
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
  projectId: number,
  commissionId: number,
  name: string
): Promise<Deliverable | number> => {
  const csrftoken = Cookies.get("csrftoken");
  if (!csrftoken) {
    console.warn("Could not find a csrf token! Request will probably fail");
  }

  try {
    const response = await axios.post<Deliverable>(
      `${API_DELIVERABLES}/bundle/new?autoname=true`,
      {
        pluto_core_project_id: projectId,
        commission_id: commissionId,
        name: name,
      },
      {
        headers: {
          "X-CSRFToken": csrftoken,
        },
        validateStatus: (status) =>
          status == 200 || status == 201 || status == 409,
      }
    );

    if (response.status == 200) {
      return response.data;
    } else if (response.status == 409) {
      //conflict - the given bundle already exists, so just return the project id
      return projectId;
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
