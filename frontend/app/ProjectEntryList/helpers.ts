import Axios from "axios";

const API = "/api";
const API_PROJECTS = `${API}/project`;
const API_PROJECTS_FILTER = `${API_PROJECTS}/list`;
const API_IS_LOGGED_IN = `${API}/isLoggedIn`;

interface ProjectsOnPage {
  page?: number;
  pageSize?: number;
  filterTerms?: FilterTerms;
}

export const getProjectsOnPage = async ({
  page = 0,
  pageSize = 25,
  filterTerms,
}: ProjectsOnPage): Promise<Project[]> => {
  try {
    const {
      status,
      data: { result },
    } = filterTerms
      ? await Axios.put<PlutoApiResponse<Project[]>>(
          `${API_PROJECTS_FILTER}?startAt=${
            page * pageSize
          }&length=${pageSize}`,
          filterTerms
        )
      : await Axios.get<PlutoApiResponse<Project[]>>(
          `${API_PROJECTS}?startAt=${page * pageSize}&length=${pageSize}`
        );

    if (status === 200) {
      return result;
    }

    throw new Error(`Could not retrieve projects. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const isLoggedIn = async (): Promise<IsLoggedIn> => {
  try {
    const { status, data } = await Axios.get<IsLoggedIn>(`${API_IS_LOGGED_IN}`);

    if (status === 200) {
      return data;
    }

    throw new Error(`Could not retrieve who is logged in. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};
