import Axios from "axios";

const API = "/api";
const API_PROJECTS = `${API}/project`;
const API_PROJECTS_FILTER = `${API_PROJECTS}/list`;

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

export const getProject = async (id: number): Promise<Project> => {
  try {
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<Project>>(`${API_PROJECTS}/${id}`);

    if (status === 200) {
      return result;
    }

    throw new Error(`Could not get project ${id}. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const updateProject = async (project: Project): Promise<void> => {
  try {
    const { status } = await Axios.put<PlutoApiResponse<void>>(
      `${API_PROJECTS}/${project.id}`,
      project
    );

    if (status !== 200) {
      throw new Error(
        `Could not update project ${project.id}: server said ${status}`
      );
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const updateProjectOpenedStatus = async (id: number): Promise<void> => {
  try {
    const { status } = await Axios.put<PlutoApiResponse<void>>(
      `${API_PROJECTS}/${id}/wasopened`
    );

    if (status !== 200) {
      throw new Error(
        `Could not update project opened status ${id}. ${status}`
      );
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};
